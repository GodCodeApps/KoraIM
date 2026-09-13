package com.kora.onsim.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Callback for microphone recognition.
 *
 * [onPartialResult] is called repeatedly while the current utterance is being
 * spoken. [onFinalResult] is called when VAD detects the end of an utterance.
 * All callbacks are delivered on the main thread.
 */
interface OnnxSimAsrListener {
    fun onReady() = Unit
    fun onListeningStarted() = Unit
    fun onAudioLevel(level: Float) = Unit
    fun onPartialResult(text: String) = Unit
    fun onFinalResult(text: String) = Unit
    fun onError(error: Throwable) = Unit
    fun onStopped() = Unit
}

/** Callback used by [OnnxSimAsr.initialize]. All callbacks run on the main thread. */
interface OnnxSimAsrInitializationListener {
    fun onInitialized() = Unit
    fun onError(error: Throwable) = Unit
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
    private val isStarting = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
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
                !isStarting.compareAndSet(false, true)
            ) {
                return false
            }
            stopRequested.set(false)
            releaseRequested.set(false)
            applicationContext = context.applicationContext
            this.listener = listener
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

    /** Stop the current recording and decode the remaining audio. */
    @JvmStatic
    fun stopListening() {
        if (isStarting.get() && !isRecording.get()) {
            stopRequested.set(true)
            return
        }
        if (!isRecording.compareAndSet(true, false)) return

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
        stopListening()
        releaseRequested.set(true)
        synchronized(stateLock) {
            listener = null
            applicationContext = null
        }
        if (!isProcessing.get() && !isStarting.get() && !isInitializing.get()) releaseModels()
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

        fun decode(samples: FloatArray): String {
            val stream = recognizer!!.createStream()
            return try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                recognizer!!.decode(stream)
                recognizer!!.getResult(stream).text
            } finally {
                stream.release()
            }
        }

        fun consumeVadSegments() {
            while (!vad!!.empty()) {
                val text = decode(vad!!.front().samples)
                speechStarted = false
                vad!!.pop()
                buffer = ArrayList()
                offset = 0
                if (text.isNotBlank()) {
                    post { this.listener?.onFinalResult(text) }
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
                    val text = decode(buffer.subList(speechStartOffset, offset).toFloatArray())
                    if (text.isNotBlank()) {
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
            isProcessing.set(false)
            isStarting.set(false)
            if (releaseRequested.get()) releaseModels()
            post { this.listener?.onStopped() }
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
        }
    }

    private fun hasRecordAudioPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun postError(target: OnnxSimAsrListener?, error: Throwable) {
        if (target != null) post { target.onError(error) }
    }

    private inline fun post(crossinline action: () -> Unit) {
        mainHandler.post { action() }
    }
}
