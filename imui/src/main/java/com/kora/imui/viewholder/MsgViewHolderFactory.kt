package com.kora.imui.viewholder

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.impl.IMMessage
import com.kora.imui.attachment.ImageAttachment
import com.kora.imui.attachment.TextAttachment
import com.kora.imui.attachment.UnknownAttachment
import kotlin.collections.get

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/12/16:18:45
 * @Description:
 */
object MsgViewHolderFactory {
    private val viewHolders =
        mutableMapOf(
            TextAttachment::class.java to MsgTextViewHolder::class.java,
            ImageAttachment::class.java to MsgImageViewHolder::class.java,
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