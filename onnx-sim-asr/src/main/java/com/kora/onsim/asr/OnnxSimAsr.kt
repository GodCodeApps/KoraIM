package com.kora.onsim.asr

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 麦克风实时语音识别回调。
 *
 * [onPartialResult] 会在当前语句识别过程中多次回调临时结果；
 * [onFinalResult] 会在 VAD 检测到当前语句结束后回调最终结果。
 * 所有回调均在主线程执行。
 */
interface OnnxSimAsrListener {
    /** ASR 引擎已经准备好，可以开始录音。 */
    fun onReady() = Unit

    /** 已经开始采集麦克风音频。 */
    fun onListeningStarted() = Unit

    /** 实时音量，范围为 0..1。 */
    fun onAudioLevel(level: Float) = Unit

    /** 当前语句的临时识别结果，可能会重复返回部分或完整文本。 */
    fun onPartialResult(text: String) = Unit

    /** 当前语句识别完成后的最终结果。 */
    fun onFinalResult(text: String) = Unit

    /** 录音、识别或模型处理发生异常。 */
    fun onError(error: Throwable) = Unit

    /** 已停止录音，并完成剩余音频的识别处理。 */
    fun onStopped() = Unit
}

/** Callback used by [OnnxSimAsr.initialize]. All callbacks run on the main thread. */
interface OnnxSimAsrInitializationListener {
    fun onInitialized() = Unit
    fun onError(error: Throwable) = Unit
}

/** Callback for converting an existing local AAC/M4A voice file to text. */
interface OnnxSimAsrFileListener {
    fun onStarted() = Unit
    fun onResult(text: String) = Unit
    fun onError(error: Throwable) = Unit
    fun onFinished() = Unit
}

/**
 * sherpa-onnx simulated-streaming ASR facade.
 *
 * The class owns microphone capture, VAD and offline decoding. The host only
 * needs to request RECORD_AUDIO and call [startListening]. Model loading is
 * lazy and runs off the main thread.
 */
object OnnxSimAsr {
    const val SAMPLE_RATE = 16_000
    const val DEFAULT_ASR_MODEL_TYPE = 15

    private const val TAG = "kora-onnx-sim-asr"
    private const val VAD_WINDOW_SIZE = 512
    private const val PARTIAL_DECODE_INTERVAL_MS = 200L
    private const val PRE_ROLL_SAMPLES = 6_400

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(3)
    private val stateLock = Any()
    private val isRecording = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)
    private val isFileTranscribing = AtomicBoolean(false)
    private val isStarting = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val stopRequestedByCaller = AtomicBoolean(false)
    private val releaseRequested = AtomicBoolean(false)
    private val sampleQueue = LinkedBlockingQueue<FloatArray>()

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var vad: Vad? = null

    @Volatile
    private var listener: OnnxSimAsrListener? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var saveToLocal = false

    @Volatile
    private var wavRecorder: PcmWavRecorder? = null

    @Volatile
    private var temporaryAudioFile: File? = null

    @Volatile
    private var finalRecognizedText = ""

    @Volatile
    private var saveDialogActivity: WeakReference<Activity>? = null

    /** True once the ASR and VAD models have been loaded successfully. */
    val isReady: Boolean
        get() = recognizer != null && vad != null

    /**
     * Initialize the ASR engine with the bundled default SenseVoice model.
     * This method is asynchronous and must complete before [startListening].
     */
    @JvmStatic
    fun initialize(context: Context): Boolean =
        initialize(context, DEFAULT_ASR_MODEL_TYPE, null)

    /** Java/Kotlin-friendly overload with an initialization callback. */
    @JvmStatic
    fun initialize(
        context: Context,
        callback: OnnxSimAsrInitializationListener?,
    ): Boolean = initialize(context, DEFAULT_ASR_MODEL_TYPE, callback)

    /** Initialize with a sherpa-onnx model type from getOfflineModelConfig(). */
    @JvmStatic
    fun initialize(
        context: Context,
        asrModelType: Int,
        callback: OnnxSimAsrInitializationListener?,
    ): Boolean {
        synchronized(stateLock) {
            if (isReady) {
                callback?.let { post { it.onInitialized() } }
                return true
            }
            if (!isInitializing.compareAndSet(false, true)) return false
            applicationContext = context.applicationContext
        }

        val appContext = context.applicationContext
        executor.execute {
            try {
                ensureInitialized(appContext, asrModelType)
                isInitializing.set(false)
                if (releaseRequested.get()) {
                    releaseModels()
                    return@execute
                }
                callback?.let { post { it.onInitialized() } }
            } catch (error: Throwable) {
                isInitializing.set(false)
                releaseModels()
                Log.e(TAG, "Failed to initialize ASR", error)
                callback?.let { post { it.onError(error) } }
            }
        }
        return true
    }

    /**
     * Start microphone recognition. Returns false if another recognition is
     * already running, stopping, or being initialized.
     *
     * Call [initialize] first. The caller must have already granted
     * [Manifest.permission.RECORD_AUDIO].
     */
    @JvmStatic
    fun startListening(
        context: Context,
        listener: OnnxSimAsrListener,
    ): Boolean = startListening(context, listener, saveToLocal = false)

    /**
     * Start microphone recognition and optionally keep the captured audio.
     *
     * When [saveToLocal] is true, [stopListening] shows a confirmation dialog
     * after the recording has been finalized. The confirmed file is saved as a
     * WAV file under the app's external Music directory.
     */
    @JvmStatic
    fun startListening(
        context: Context,
        listener: OnnxSimAsrListener,
        saveToLocal: Boolean,
    ): Boolean {
        if (!hasRecordAudioPermission(context)) {
            postError(listener, SecurityException("RECORD_AUDIO permission has not been granted"))
            return false
        }
        if (!isReady) {
            postError(listener, IllegalStateException("Call OnnxSimAsr.initialize() before startListening()"))
            return false
        }

        synchronized(stateLock) {
            if (isInitializing.get() || isRecording.get() || isProcessing.get() ||
                isFileTranscribing.get() ||
                !isStarting.compareAndSet(false, true)
            ) {
                return false
            }
            stopRequested.set(false)
            stopRequestedByCaller.set(false)
            releaseRequested.set(false)
            finalRecognizedText = ""
            applicationContext = context.applicationContext
            this.listener = listener
            this.saveToLocal = saveToLocal
            saveDialogActivity = WeakReference(findActivity(context))
        }

        val context = context.applicationContext
        executor.execute {
            try {
                if (stopRequested.get() || releaseRequested.get()) {
                    isStarting.set(false)
                    if (releaseRequested.get()) releaseModels()
                    post { this.listener?.onStopped() }
                    return@execute
                }
                post { this.listener?.onReady() }
                startAudioCapture()
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to start ASR", error)
                isStarting.set(false)
                postError(listener, error)
            }
        }
        return true
    }

    /** Convert a local AAC/M4A voice file to Chinese text. */
    @JvmStatic
    fun transcribeAudio(
        context: Context,
        audioPath: String,
        listener: OnnxSimAsrFileListener,
    ): Boolean {
        if (audioPath.isBlank()) {
            postFileError(listener, IllegalArgumentException("audioPath is empty"))
            return false
        }
        if (!isReady) {
            postFileError(listener, IllegalStateException("Call OnnxSimAsr.initialize() before transcribeAudio()"))
            return false
        }

        synchronized(stateLock) {
            if (isInitializing.get() || isRecording.get() || isProcessing.get() ||
                isStarting.get() || !isFileTranscribing.compareAndSet(false, true)
            ) {
                postFileError(listener, IllegalStateException("ASR is busy"))
                return false
            }
        }

        val appContext = context.applicationContext
        post { listener.onStarted() }
        executor.execute {
            try {
                val samples = AudioFileDecoder.decode(audioPath)
                val text = decodeSamples(samples)
                if (text.isBlank()) error("No speech was recognized")
                post { listener.onResult(text) }
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to transcribe audio file", error)
                post { listener.onError(error) }
            } finally {
                isFileTranscribing.set(false)
                if (releaseRequested.get()) releaseModels()
                post { listener.onFinished() }
            }
        }
        // Keep the application context referenced for the duration of the task.
        applicationContext = appContext
        return true
    }

    /** Stop the current recording and decode the remaining audio. */
    @JvmStatic
    fun stopListening() {
        stopListening(promptToSave = true)
    }

    /** Stops listening without showing the save dialog, for lifecycle cleanup. */
    @JvmStatic
    fun stopListening(promptToSave: Boolean) {
        if (isStarting.get() && !isRecording.get()) {
            stopRequestedByCaller.set(promptToSave)
            stopRequested.set(true)
            return
        }
        if (!isRecording.compareAndSet(true, false)) return

        stopRequestedByCaller.set(promptToSave)

        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Failed to stop AudioRecord", error)
        }
    }

    /** Stop recording and release model/native resources after pending decoding finishes. */
    @JvmStatic
    fun release() {
        stopListening(promptToSave = false)
        releaseRequested.set(true)
        synchronized(stateLock) {
            listener = null
            applicationContext = null
            saveDialogActivity = null
        }
        if (!isProcessing.get() && !isStarting.get() && !isInitializing.get() &&
            !isFileTranscribing.get()
        ) releaseModels()
    }

    private fun ensureInitialized(context: Context, asrModelType: Int) {
        if (isReady) return

        synchronized(stateLock) {
            if (isReady) return
            Log.i(TAG, "Initializing sherpa-onnx ASR, model type=$asrModelType")
            recognizer = SherpaAsrModel.createRecognizer(context, asrModelType)
            vad = SherpaAsrModel.createVad(context.assets)
            Log.i(TAG, "sherpa-onnx ASR initialized")
        }
    }

    private fun startAudioCapture() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferSize > 0) { "The device does not support 16 kHz microphone input" }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            record.release()
            "Failed to initialize AudioRecord"
        }

        if (stopRequested.get() || releaseRequested.get()) {
            record.release()
            isStarting.set(false)
            if (releaseRequested.get()) releaseModels()
            post { this.listener?.onStopped() }
            return
        }

        sampleQueue.clear()
        vad!!.reset()
        if (saveToLocal) {
            val tempFile = File(
                applicationContext?.cacheDir ?: error("Application context is unavailable"),
                "onnx_sim_asr_${System.currentTimeMillis()}.wav",
            )
            wavRecorder = PcmWavRecorder(tempFile, SAMPLE_RATE, 1)
            temporaryAudioFile = tempFile
        } else {
            wavRecorder = null
            temporaryAudioFile = null
        }
        audioRecord = record
        isStarting.set(false)
        isRecording.set(true)
        isProcessing.set(true)
        post { this.listener?.onListeningStarted() }

        executor.execute { captureAudio(record) }
        executor.execute { recognizeAudio() }
    }

    private fun captureAudio(record: AudioRecord) {
        val buffer = ShortArray(SAMPLE_RATE / 10)
        try {
            record.startRecording()
            while (isRecording.get()) {
                val count = record.read(buffer, 0, buffer.size)
                if (count > 0) {
                    wavRecorder?.append(buffer, count)
                    var peak = 0f
                    val samples = FloatArray(count) { index ->
                        val sample = buffer[index] / 32768.0f
                        peak = maxOf(peak, kotlin.math.abs(sample))
                        sample
                    }
                    post { this.listener?.onAudioLevel((peak * 2.5f).coerceIn(0f, 1f)) }
                    sampleQueue.put(samples)
                }
            }
        } catch (error: Throwable) {
            if (isRecording.getAndSet(false)) {
                Log.e(TAG, "Audio capture failed", error)
                postError(listener, error)
            }
        } finally {
            finishWavRecording()
            sampleQueue.offer(FloatArray(0))
            runCatching { record.release() }
            if (audioRecord === record) audioRecord = null
        }
    }

    private fun recognizeAudio() {
        var buffer = ArrayList<Float>()
        var offset = 0
        var speechStarted = false
        var speechStartOffset = 0
        var lastDecodeTime = System.currentTimeMillis()
        val finalTexts = ArrayList<String>()
        var pendingPartialText = ""

        fun consumeVadSegments() {
            while (!vad!!.empty()) {
                val text = decodeSamples(vad!!.front().samples)
                speechStarted = false
                vad!!.pop()
                buffer = ArrayList()
                offset = 0
                if (text.isNotBlank()) {
                    finalTexts += text
                    pendingPartialText = ""
                    post { this.listener?.onFinalResult(text) }
                } else if (pendingPartialText.isNotBlank()) {
                    // VAD 结束时如果最终解码为空，保留最近一次实时识别结果。
                    finalTexts += pendingPartialText
                    pendingPartialText = ""
                }
            }
        }

        try {
            while (true) {
                val samples = sampleQueue.take()
                if (samples.isEmpty()) break

                buffer.addAll(samples.asList())
                while (offset + VAD_WINDOW_SIZE <= buffer.size) {
                    vad!!.acceptWaveform(
                        buffer.subList(offset, offset + VAD_WINDOW_SIZE).toFloatArray(),
                    )
                    offset += VAD_WINDOW_SIZE
                    if (!speechStarted && vad!!.isSpeechDetected()) {
                        speechStarted = true
                        speechStartOffset = (offset - PRE_ROLL_SAMPLES).coerceAtLeast(0)
                        lastDecodeTime = System.currentTimeMillis()
                    }
                }

                if (speechStarted &&
                    System.currentTimeMillis() - lastDecodeTime >= PARTIAL_DECODE_INTERVAL_MS &&
                    speechStartOffset < offset
                ) {
                    val text = decodeSamples(buffer.subList(speechStartOffset, offset).toFloatArray())
                    if (text.isNotBlank()) {
                        pendingPartialText = text
                        post { this.listener?.onPartialResult(text) }
                    }
                    lastDecodeTime = System.currentTimeMillis()
                }
                consumeVadSegments()
            }

            vad!!.flush()
            consumeVadSegments()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.i(TAG, "Recognition stopped")
        } catch (error: Throwable) {
            Log.e(TAG, "Recognition failed", error)
            postError(listener, error)
        } finally {
            finalRecognizedText = (finalTexts + pendingPartialText)
                .joinToString(separator = "")
            val shouldPromptForSave = stopRequestedByCaller.get() &&
                saveToLocal && !releaseRequested.get()
            isProcessing.set(false)
            isStarting.set(false)
            if (releaseRequested.get()) releaseModels()
            post {
                this.listener?.onStopped()
                if (shouldPromptForSave) {
                    showSaveAudioDialog()
                } else {
                    discardTemporaryAudioFile()
                }
            }
        }
    }

    private fun finishWavRecording() {
        val recorder = wavRecorder
        wavRecorder = null
        runCatching { recorder?.close() }
            .onFailure { error -> Log.e(TAG, "Failed to finalize WAV recording", error) }

        val file = temporaryAudioFile
        if (file == null || !file.exists() || file.length() <= WAV_HEADER_SIZE) {
            file?.delete()
            temporaryAudioFile = null
        }
    }

    private fun showSaveAudioDialog() {
        val file = temporaryAudioFile
        val activity = saveDialogActivity?.get()
        if (file == null || !file.exists()) return
        if (activity == null || activity.isFinishing ||
            (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 &&
                activity.isDestroyed)
        ) {
            Log.w(TAG, "Cannot show save dialog because the recording Activity is unavailable")
            discardTemporaryAudioFile()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("保存录音")
            .setMessage("是否将本次语音识别录音保存到本地？")
            .setNegativeButton("取消") { _, _ -> discardTemporaryAudioFile() }
            .setPositiveButton("保存") { _, _ -> saveAudioFile(file) }
            .setOnCancelListener { discardTemporaryAudioFile() }
            .show()
    }

    private fun saveAudioFile(source: File) {
        val context = applicationContext
        if (context == null) {
            Log.e(TAG, "Cannot save recording: application context is unavailable")
            discardTemporaryAudioFile()
            return
        }

        executor.execute {
            try {
                val directory = File(
                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                        ?: File(context.filesDir, "Music"),
                    "KoraIM",
                )
                check(directory.mkdirs() || directory.isDirectory) {
                    "Cannot create audio directory: ${directory.absolutePath}"
                }
                val name = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                    .format(Date())
                val target = File(directory, "asr_$name.wav")
                val textTarget = File(directory, "${target.nameWithoutExtension}.txt")
                source.copyTo(target, overwrite = false)
                textTarget.writeText(finalRecognizedText, Charsets.UTF_8)
                source.delete()
                temporaryAudioFile = null
                Log.i(TAG, "Audio saved to: ${target.absolutePath}")
                Log.i(TAG, "Recognized text saved to: ${textTarget.absolutePath}")
                post {
                    android.widget.Toast.makeText(
                        context,
                        "录音和识别文字保存成功：${target.absolutePath}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to save audio recording", error)
            }
        }
    }

    private fun discardTemporaryAudioFile() {
        temporaryAudioFile?.let { file ->
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete temporary audio file: ${file.absolutePath}")
            }
        }
        temporaryAudioFile = null
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    private fun decodeSamples(samples: FloatArray): String {
        val currentRecognizer = recognizer ?: error("ASR recognizer is not initialized")
        val stream = currentRecognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            currentRecognizer.decode(stream)
            currentRecognizer.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    private fun releaseModels() {
        synchronized(stateLock) {
            recognizer?.release()
            recognizer = null
            vad?.release()
            vad = null
            sampleQueue.clear()
            stopRequested.set(false)
            releaseRequested.set(false)
            stopRequestedByCaller.set(false)
        }
    }

    private const val WAV_HEADER_SIZE = 44L

    private fun hasRecordAudioPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun postError(target: OnnxSimAsrListener?, error: Throwable) {
        if (target != null) post { target.onError(error) }
    }

    private fun postFileError(target: OnnxSimAsrFileListener, error: Throwable) {
        post { target.onError(error) }
        post { target.onFinished() }
    }

    private inline fun post(crossinline action: () -> Unit) {
        mainHandler.post { action() }
    }
}
