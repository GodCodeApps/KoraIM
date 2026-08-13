package com.kora.imui

import com.kora.imcore.ImSdkImpl
import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.constant.MsgType
import com.kora.imcore.constant.SessionType
import com.kora.imcore.db.Message
import com.kora.imcore.impl.IMMessage
import com.kora.imui.attachment.ImageAttachment
import com.kora.imui.attachment.TextAttachment
import com.kora.imui.attachment.VideoAttachment
import com.kora.imcore.constant.MsgStatus

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/20:09:32
 * @Description:构建消息实例
 */
object MessageBuilder {
    fun createVideoMessage(
        sessionId: String,
        @SessionType sessionType: Int,
        receiverId: String,
        attachment: VideoAttachment
    ): IMMessage = Message(
        sessionId = sessionId,
        sessionType = sessionType,
        senderId = ImSdkImpl.getAccount() ?: "",
        receiverId = receiverId,
        type = MsgType.VIDEO,
        direct = MsgDirection.OUT,
        status = MsgStatus.SENDING,
        time = System.currentTimeMillis(),
        attachment = attachment.toJson(false)
    )

    fun createTextMessage(
        sessionId: String,
        @SessionType sessionType: Int = SessionType.None,
        @MsgType msgType: Int = MsgType.TEXT,
        @MsgDirection msgDirect: Int = MsgDirection.OUT,
        @MsgStatus msgStatus: Int = MsgStatus.SENDING,
        receiverId: String = sessionId,
        msg: String = ""
    ): IMMessage {
        return Message(
            sessionId = sessionId,
            sessionType = sessionType,
            type = msgType,
            direct = msgDirect,
            status = msgStatus,
            senderId = ImSdkImpl.getAccount() ?: "",
            receiverId = receiverId,
            time = System.currentTimeMillis(),
            attachment = TextAttachment().apply { content = msg }.toJson(true)
        )
    }

    fun createImageMessage(
        sessionId: String,
        @SessionType sessionType: Int = SessionType.None,
        @MsgType msgType: Int = MsgType.IMAGE,
        @MsgDirection msgDirect: Int = MsgDirection.OUT,
        @MsgStatus msgStatus: Int = MsgStatus.SENDING,
        receiverId: String = sessionId,
        localPath: String = "",
        mWidth: Int = 0,
        mHeight: Int = 0
    ): IMMessage {
        return Message(
            sessionId = sessionId,
            sessionType = sessionType,
            type = msgType,
            direct = msgDirect,
            status = msgStatus,
            senderId = ImSdkImpl.getAccount() ?: "",
            receiverId = receiverId,
            time = System.currentTimeMillis(),
            attachment = ImageAttachment().apply {
                path = localPath
                width = mWidth
                height = mHeight
            }.toJson(true)
        )
    }
}
