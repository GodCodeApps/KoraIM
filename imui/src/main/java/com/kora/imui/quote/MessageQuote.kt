package com.kora.imui.quote

import com.kora.imcore.constant.MsgType
import com.kora.imcore.impl.IMMessage
import com.kora.imui.attachment.*
import org.json.JSONObject

data class MessageQuote(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val messageType: Int,
    val preview: String
) {
    fun displayText(): String = "$senderName：$preview"
    fun toJson(): JSONObject = JSONObject().apply {
        put("messageId", messageId); put("senderId", senderId); put("senderName", senderName)
        put("messageType", messageType); put("preview", preview)
    }

    companion object {
        fun fromMessage(message: IMMessage, senderName: String) = MessageQuote(
            message.getMsgId(), message.senderId, senderName.ifBlank { message.senderId },
            message.getMsgType(), previewOf(message)
        )

        fun read(extra: String?): MessageQuote? {
            if (extra.isNullOrBlank()) return null
            return try {
                val q = JSONObject(extra).optJSONObject("quote") ?: return null
                MessageQuote(q.optString("messageId"), q.optString("senderId"),
                    q.optString("senderName").ifBlank { q.optString("senderId") },
                    q.optInt("messageType", MsgType.UNDEF), q.optString("preview", "[消息]"))
                    .takeIf { it.messageId.isNotBlank() }
            } catch (_: Exception) { null }
        }

        fun write(extra: String?, quote: MessageQuote): String {
            val root = try { JSONObject(extra.orEmpty()) } catch (_: Exception) { JSONObject() }
            return root.put("quote", quote.toJson()).toString()
        }

        private fun previewOf(message: IMMessage): String = when (val a = message.getAttachment()) {
            is TextAttachment -> a.content
            is ImageAttachment -> "[图片]"
            is VideoAttachment -> "[视频]"
            is VoiceAttachment -> "[语音]"
            is FileAttachment -> if (a.name.isBlank()) "[文件]" else "[文件] ${a.name}"
            is LocationAttachment -> "[位置]"
            is CardAttachment -> if (a.nickname.isBlank()) "[个人名片]" else "[个人名片] ${a.nickname}"
            is RedPacketAttachment -> "[红包]"
            else -> "[消息]"
        }.replace('\n', ' ').take(120)
    }
}

internal object QuoteActionDispatcher {
    var onQuote: ((IMMessage) -> Unit)? = null
    var onLocate: ((String) -> Boolean)? = null
    var onReedit: ((String) -> Unit)? = null
}
