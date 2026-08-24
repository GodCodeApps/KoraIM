package com.kora.imui

import com.kora.imcore.IMClient
import com.kora.imcore.constant.MsgStatus
import com.kora.imcore.constant.MsgType
import com.kora.imcore.db.Message
import com.kora.imui.attachment.ImageAttachment
import com.kora.imui.attachment.VideoAttachment
import com.kora.imui.attachment.VoiceAttachment
import com.kora.imui.provider.ImageUploadRequest
import com.kora.imui.provider.VideoUploadRequest
import com.kora.imui.provider.VoiceUploadRequest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 多媒体消息发送器（图片、视频、语音）：
 * 负责在后台协程中上传本地多媒体文件，获取远程 URL 后再提交给 imcore 发送。
 */
object IMMediaMessageSender {
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val UPLOAD_TIMEOUT_MS = 120_000L

    /** 将多媒体消息推入后台队列异步上传并发送（不受页面销毁影响） */
    fun enqueue(message: Message) {
        uploadScope.launch { send(message) }
    }

    /** 核心上传与发送流程：入库 -> 上传附件 -> 组装远程 URL -> 调用 IMClient.sendMessage */
    suspend fun send(message: Message) {
        if (!running.add(message.messageId)) return
        try {
            message.status = MsgStatus.SENDING
            IMClient.saveMessage(message)
            val provider = ImUIKitImpl.getMediaMessageProvider()
                ?: error("IMMediaMessageProvider is not configured")
            withTimeout(UPLOAD_TIMEOUT_MS) {
                prepareRemoteAttachment(message, provider)
            }
            IMClient.sendMessage(message)
        } catch (_: Throwable) {
            message.status = MsgStatus.FAIL
            // A lifecycle cancellation or timeout must not prevent FAIL from reaching SQLite/UI.
            withContext(NonCancellable) { IMClient.saveMessage(message) }
        } finally {
            running.remove(message.messageId)
        }
    }

    private suspend fun prepareRemoteAttachment(
        message: Message,
        provider: com.kora.imui.provider.IMMediaMessageProvider
    ) {
        when (message.type) {
                MsgType.IMAGE -> {
                    val attachment = ImageAttachment(message.attachment)
                    if (attachment.remoteUrl.isBlank()) {
                        require(attachment.localPath.isNotBlank()) { "Image localPath is empty" }
                        val result = provider.uploadImage(
                            ImageUploadRequest(
                                attachment.localPath, attachment.width, attachment.height,
                                attachment.size, attachment.mimeType
                            )
                        )
                        require(result.remoteUrl.isNotBlank()) { "Image remoteUrl is empty" }
                        attachment.remoteUrl = result.remoteUrl
                    }
                    // Network payload must never contain a path that only exists on the sender's device.
                    message.attachment = attachment.toJson(true)
                }
                MsgType.VIDEO -> {
                    val attachment = VideoAttachment(message.attachment)
                    if (attachment.remoteUrl.isBlank()) {
                        require(attachment.localPath.isNotBlank()) { "Video localPath is empty" }
                        val result = provider.uploadVideo(
                            VideoUploadRequest(
                                attachment.localPath, attachment.localCoverPath, attachment.duration,
                                attachment.width, attachment.height, attachment.size, attachment.mimeType
                            )
                        )
                        require(result.remoteUrl.isNotBlank()) { "Video remoteUrl is empty" }
                        attachment.remoteUrl = result.remoteUrl
                        attachment.remoteCoverUrl = result.remoteCoverUrl
                    }
                    message.attachment = attachment.toJson(true)
                }
                MsgType.VOICE -> {
                    val attachment = VoiceAttachment(message.attachment)
                    if (attachment.remoteUrl.isBlank()) {
                        require(attachment.localPath.isNotBlank()) { "Voice localPath is empty" }
                        val result = provider.uploadVoice(
                            VoiceUploadRequest(
                                attachment.localPath, attachment.duration, attachment.size,
                                attachment.mimeType
                            )
                        )
                        require(result.remoteUrl.isNotBlank()) { "Voice remoteUrl is empty" }
                        attachment.remoteUrl = result.remoteUrl
                    }
                    message.attachment = attachment.toJson(true)
                }
                com.kora.imui.attachment.LocationAttachment.TYPE_LOCATION -> {
                    val attachment = com.kora.imui.attachment.LocationAttachment(message.attachment)
                    if (attachment.remoteSnapshotUrl.isBlank() && attachment.snapshotPath.isNotBlank()) {
                        val result = provider.uploadImage(
                            ImageUploadRequest(
                                attachment.snapshotPath, 0, 0,
                                0, "image/png"
                            )
                        )
                        if (result.remoteUrl.isNotBlank()) {
                            attachment.remoteSnapshotUrl = result.remoteUrl
                        }
                    }
                    message.attachment = attachment.toJson(true)
                }
                else -> error("Message is not a supported media type")
        }
    }

    fun isMedia(message: Message): Boolean =
        message.type == MsgType.IMAGE || message.type == MsgType.VIDEO || message.type == MsgType.VOICE || message.type == com.kora.imui.attachment.LocationAttachment.TYPE_LOCATION
}
