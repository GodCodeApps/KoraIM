package com.kora.imcore.db


import com.google.gson.Gson
import com.kora.imcore.ImSdkImpl
import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.constant.MsgStatus
import com.kora.imcore.constant.MsgType
import com.kora.imcore.constant.SessionType
import com.kora.imcore.impl.IMMessage
import java.util.*

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/21:18:04
 * @Description:
 */
class Message : IMMessage {
    var id: Long = 0
    var messageId: String = ""
    var sessionType: Int = SessionType.None
    var sessionId: String = ""
    var type: Int = MsgType.UNDEF
    var direct: Int = MsgDirection.IN
    var status: Int = MsgStatus.SENDING
    var time: Long = 0
    var attachment: String = ""
    var extra: String = ""
    var account: String = ""

    constructor(
        messageId: String = UUID.randomUUID().toString(),
        sessionType: Int,
        sessionId: String,
        account: String = "",
        type: Int,
        direct: Int,
        status: Int,
        time: Long,
        attachment: String,
        extra: String = ""
    ) {
        this.messageId = messageId
        this.sessionType = sessionType
        this.sessionId = sessionId
        this.account = account
        this.type = type
        this.direct = direct
        this.status = status
        this.time = time
        this.attachment = attachment
        this.extra = extra
    }

    constructor()

    override fun getIMSessionType(): Int {
        return sessionType
    }

    override fun getIMSessionId(): String {
        return sessionId
    }

    override fun getMsgId(): String {
        return messageId
    }

    override fun getMsgType(): Int {
        return type
    }

    override fun getFromAccount(): String {
        return account
    }


    override fun getMsgDirection(): Int {
        return direct
    }

    override fun getMsgStatus(): Int {
        return status
    }

    override fun getMsgTime(): Long {
        return time
    }

    override fun getMsgExtra(): String {
        return extra
    }

    override fun getAttachment(): MsgAttachment? {
        ImSdkImpl.getMsgAttachmentList().forEach {
            val newInstance =
                it.javaClass.getConstructor(String::class.java).newInstance(getMessage().attachment)
            if (newInstance.getMsgType() == getMsgType()) {
                return newInstance
            }
        }
        return null
    }

    override fun getMessage(): Message {
        return this
    }

    override fun setMsgStatus(status: Int) {
        this.status = status
    }

    override fun toString(): String {
        return Gson().toJson(this)
    }
}
