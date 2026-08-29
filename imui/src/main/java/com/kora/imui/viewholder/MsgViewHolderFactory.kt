package com.kora.imui.viewholder

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.impl.IMMessage
import com.kora.imui.attachment.ImageAttachment
import com.kora.imui.attachment.TextAttachment
import com.kora.imui.attachment.UnknownAttachment
import com.kora.imui.attachment.VoiceAttachment
import com.kora.imui.attachment.VideoAttachment
import com.kora.imui.attachment.FileAttachment
import com.kora.imui.attachment.RedPacketAttachment
import com.kora.imui.attachment.TipAttachment
import kotlin.collections.get

/**
 * 消息气泡 ViewHolder 注册工厂。
 * 负责维护附件类型与 ViewHolder 的映射关系，支持业务方动态注册自定义气泡。
 */
object MsgViewHolderFactory {
    private val viewHolders =
        mutableMapOf(
            TextAttachment::class.java to MsgTextViewHolder::class.java,
            ImageAttachment::class.java to MsgImageViewHolder::class.java,
            VoiceAttachment::class.java to MsgVoiceViewHolder::class.java,
            VideoAttachment::class.java to MsgVideoViewHolder::class.java,
            FileAttachment::class.java to MsgFileViewHolder::class.java,
            RedPacketAttachment::class.java to MsgRedPacketViewHolder::class.java,
            TipAttachment::class.java to MsgTipViewHolder::class.java,
            com.kora.imui.attachment.CardAttachment::class.java to MsgCardViewHolder::class.java,
            com.kora.imui.attachment.LocationAttachment::class.java to MsgLocationViewHolder::class.java,
            UnknownAttachment::class.java to MsgUnknownViewHolder::class.java
        )

    fun register(attach: Class<out MsgAttachment>, viewHolder: Class<out MsgViewHolderBase>) {
        viewHolders[attach] = viewHolder
    }

    fun getViewHolderByType(message: IMMessage): Class<out MsgViewHolderBase?>? {
        if (message.getMessage().recalled) return MsgRecallViewHolder::class.java
        var clazz: Class<out MsgAttachment?>? =
            getAttachmentByType(message)
        var viewHolder = viewHolders[clazz]
        return viewHolder ?: MsgUnknownViewHolder::class.java
    }

    fun getAttachmentByType(message: IMMessage): Class<out MsgAttachment>? {
        getAllAttachments().forEach {
            val newInstance =
                it.getConstructor(String::class.java).newInstance(message.getMessage().attachment)
            if (newInstance.getMsgType() == message.getMsgType()) {
                return newInstance::class.java
            }
        }
        return null
    }

    fun getAttachment(message: IMMessage): MsgAttachment? {
        getAllAttachments().forEach {
            val newInstance =
                it.getConstructor(String::class.java).newInstance(message.getMessage().attachment)
            if (newInstance.getMsgType() == message.getMsgType()) {
                return newInstance
            }
        }
        return null
    }

    fun getAllViewHolders(): List<Class<out MsgViewHolderBase>> {
        return (viewHolders.values + MsgRecallViewHolder::class.java).distinct()
    }

    fun getAllAttachments(): List<Class<out MsgAttachment>> {
        return viewHolders.keys.toList()
    }

}
