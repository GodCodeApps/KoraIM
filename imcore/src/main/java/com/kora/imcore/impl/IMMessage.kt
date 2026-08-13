package com.kora.imcore.impl

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.db.Message
import java.io.Serializable

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/15:09:39
 * @Description:
 */
interface IMMessage : Serializable {
    val senderId: String
    val receiverId: String

    fun getMsgId(): String
    fun getIMSessionType(): Int
    fun getIMSessionId(): String
    fun getMsgType(): Int
    fun getMsgDirection(): Int
    fun getMsgStatus(): Int
    fun getMsgTime(): Long
    fun getMsgExtra(): String
    fun getAttachment(): MsgAttachment?
    fun getMessage(): Message
    fun setMsgStatus(status: Int)
}
