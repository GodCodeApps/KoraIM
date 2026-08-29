package com.kora.imui.provider

import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.constant.MsgType
import com.kora.imcore.db.Conversation
import com.kora.imcore.db.Message
import com.kora.imcore.provider.IMConversationDigestProvider
import com.kora.imui.attachment.ImageAttachment
import com.kora.imui.attachment.RedPacketAttachment
import com.kora.imui.attachment.TextAttachment
import com.kora.imui.attachment.TipAttachment
import com.kora.imui.attachment.VideoAttachment
import com.kora.imui.attachment.FileAttachment
import com.kora.imui.attachment.VoiceAttachment

/**
 * 会话列表最后一条消息摘要格式化器：
 * 位于 imui 业务展示层，用于根据不同 MsgAttachment / MsgType 动态解析和渲染预览文案。
 * 支持根据当前登录者视角动态区分第一人称/第三人称（如“你领取了好友的红包” vs “好友领取了你的红包”）。
 */
object ConversationDigestFormatter : IMConversationDigestProvider {

    override fun format(message: Message, ownerId: String): String = formatMessage(message, ownerId)

    /**
     * 格式化会话预览文本
     */
    fun format(conversation: Conversation): String {
        // The core provider computes this when the conversation is updated;
        // avoid one message-table query per conversation while rendering lists.
        return conversation.lastMessagePreview.ifEmpty { "[消息]" }
    }

    /**
     * 根据具体消息模型及当前用户账号格式化摘要
     */
    fun formatMessage(message: Message, currentOwnerId: String? = null): String {
        if (message.recalled) {
            return if (!currentOwnerId.isNullOrBlank() && message.senderId == currentOwnerId) "你撤回了一条消息" else "对方撤回了一条消息"
        }
        val attachment = message.getAttachment()
        return when (attachment) {
            is TextAttachment -> attachment.content
            is RedPacketAttachment -> "[红包]"
            is TipAttachment -> {
                if (attachment.redPacketMsgId.isNotEmpty()) {
                    val isOut = if (!currentOwnerId.isNullOrEmpty() && message.senderId.isNotEmpty()) {
                        message.senderId == currentOwnerId
                    } else {
                        message.direct == MsgDirection.OUT
                    }
                    if (isOut) {
                        "你领取了好友的红包"
                    } else {
                        "好友领取了你的红包"
                    }
                } else {
                    attachment.content.ifEmpty { "[提示消息]" }
                }
            }
            is ImageAttachment -> "[图片]"
            is VideoAttachment -> "[视频]"
            is FileAttachment -> "[文件] ${attachment.name}".trim()
            is VoiceAttachment -> "[语音]"
            is com.kora.imui.attachment.CardAttachment -> {
                if (attachment.nickname.isNotBlank()) "[个人名片] ${attachment.nickname}" else "[个人名片]"
            }
            is com.kora.imui.attachment.LocationAttachment -> "[位置]"
            else -> {
                when (message.type) {
                    MsgType.TEXT -> message.attachment
                    MsgType.IMAGE -> "[图片]"
                    MsgType.VIDEO -> "[视频]"
                    FileAttachment.TYPE_FILE -> "[文件]"
                    MsgType.VOICE -> "[语音]"
                    MsgType.RED_PACKET -> "[红包]"
                    MsgType.TIP -> "[提示消息]"
                    else -> message.attachment.takeIf { it.isNotBlank() } ?: "[消息]"
                }
            }
        }
    }
}
