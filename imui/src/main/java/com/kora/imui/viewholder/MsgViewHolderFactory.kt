package com.kora.imui.viewholder

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.impl.IMMessage
import com.kora.imui.attachment.ImageAttachment
import com.kora.imui.attachment.TextAttachment
import com.kora.imui.attachment.UnknownAttachment
import com.kora.imui.attachment.VoiceAttachment
import com.kora.imui.attachment.VideoAttachment
import com.kora.imui.attachment.RedPacketAttachment
import kotlin.collections.get

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/16:18:45
 * @Description:
 */
object MsgViewHolderFactory {
    private val viewHolders =
        mutableMapOf(
            TextAttachment::class.java to MsgTextViewHolder::class.java,
            ImageAttachment::class.java to MsgImageViewHolder::class.java,
            VoiceAttachment::class.java to MsgVoiceViewHolder::class.java,
            VideoAttachment::class.java to MsgVideoViewHolder::class.java,
            RedPacketAttachment::class.java to MsgRedPacketViewHolder::class.java,
            UnknownAttachment::class.java to MsgUnknownViewHolder::class.java
        )

    fun register(attach: Class<out MsgAttachment>, viewHolder: Class<out MsgViewHolderBase>) {
        viewHolders[attach] = viewHolder
    }

    fun getViewHolderByType(message: IMMessage): Class<out MsgViewHolderBase?>? {
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
        return viewHolders.values.toList()
    }

    fun getAllAttachments(): List<Class<out MsgAttachment>> {
        return viewHolders.keys.toList()
    }

}
