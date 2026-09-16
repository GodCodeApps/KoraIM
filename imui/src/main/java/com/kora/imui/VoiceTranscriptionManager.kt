package com.kora.imui

import android.content.Context
import com.kora.imcore.impl.IMMessage
import com.kora.imui.attachment.VoiceAttachment
import com.kora.onsim.asr.OnnxSimAsr
import com.kora.onsim.asr.OnnxSimAsrFileListener
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resolves a voice message to a local file and runs the local ASR file API. */
internal object VoiceTranscriptionManager {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap.newKeySet<String>()

    fun transcribe(
        context: Context,
        message: IMMessage,
        force: Boolean,
        onStarted: () -> Unit,
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onFinished: () -> Unit,
    ) {
        val appContext = context.applicationContext
        val messageId = message.getMsgId()
        if (!force) {
            val cached = VoiceTranscriptionStore.get(appContext, messageId)
            if (cached.isNotBlank()) {
                onResult(cached)
                onFinished()
                return
            }
        }
        if (!running.add(messageId)) {
            onError(IllegalStateException("这条语音正在转文字"))
            onFinished()
            return
        }

        onStarted()
        scope.launch {
            try {
                val attachment = message.getAttachment() as? VoiceAttachment
                    ?: error("语音消息附件无效")
                val audioFile = resolveAudioFile(appContext, messageId, attachment)
                withContext(Dispatchers.Main) {
                    OnnxSimAsr.transcribeAudio(
                        appContext,
                        audioFile.absolutePath,
                        object : OnnxSimAsrFileListener {
                            override fun onResult(text: String) {
                                val result = text.trim()
                                if (result.isNotBlank()) {
                                    VoiceTranscriptionStore.put(appContext, messageId, result)
                                    onResult(result)
                                }
                            }

                            override fun onError(error: Throwable) {
                                onError(error)
                            }

                            override fun onFinished() {
                                running.remove(messageId)
                                onFinished()
                            }
                        },
                    )
                }
            } catch (error: Throwable) {
                running.remove(messageId)
                withContext(Dispatchers.Main) {
                    onError(error)
                    onFinished()
                }
            }
        }
    }

    private fun resolveAudioFile(
        context: Context,
        messageId: String,
        attachment: VoiceAttachment,
    ): File {
        val localPath = attachment.localPath
        if (localPath.isNotBlank() && File(localPath).isFile) return File(localPath)

        val remoteUrl = attachment.remoteUrl
        if (remoteUrl.isBlank()) error("语音文件不存在")
        val remoteFile = File(remoteUrl)
        if (remoteFile.isFile) return remoteFile

        val cacheDir = File(context.cacheDir, "voice_asr").apply { mkdirs() }
        val target = File(cacheDir, "$messageId.audio")
        if (target.isFile && target.length() > 0L) return target

        val partial = File(target.absolutePath + ".part")
        val connection = URL(remoteUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.connect()
            require(connection.responseCode in 200..299) {
                "下载语音失败，HTTP ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
            require(partial.length() > 0L) { "下载的语音文件为空" }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            return target
        } finally {
            connection.disconnect()
            if (partial.exists() && !target.exists()) partial.delete()
        }
    }
}
