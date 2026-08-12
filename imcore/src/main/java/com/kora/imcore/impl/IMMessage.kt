package com.kora.imcore.impl

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.db.Message
import java.io.Serializable

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2026/07/15:09:39
 * @Description:
 */
interface IMMessage : Serializable {
    fun getMsgId(): String
    fun getIMSessionType(): Int
    fun getIMSessionId(): String
    fun getMsgType(): Int
    fun getFromAccount(): String
    fun getMsgDirection(): Int
    fun getMsgStatus(): Int
    fun getMsgTime(): Long
    fun getMsgExtra(): String
    fun getAttachment(): MsgAttachment?
    fun getMessage(): Message
    fun setMsgStatus(status: Int)
}