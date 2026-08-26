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
import com.kora.imui.attachment.FileAttachment
import com.kora.imui.attachment.RedPacketAttachment
import com.kora.imcore.constant.MsgStatus
import com.kora.imcore.attachment.MsgAttachment

/**
 * 消息构建工厂，提供各类消息（文本、图片、视频、红包、提示）的便捷创建方法。
 */
object MessageBuilder {

    /**
     * Creates a new outgoing message from an existing supported attachment.
     * A new message ID and timestamp are always generated; the source message is never reused.
     */
    fun createForwardedMessage(
        sessionId: String,
        @SessionType sessionType: Int,
        receiverId: String,
        attachment: MsgAttachment
    ): IMMessage = Message(
        sessionId = sessionId,
        sessionType = sessionType,
        senderId = ImSdkImpl.getAccount() ?: "",
        receiverId = receiverId,
        type = attachment.getMsgType(),
        direct = MsgDirection.OUT,
        status = MsgStatus.SENDING,
        time = System.currentTimeMillis(),
        attachment = attachment.toJson(false)
    )

    fun createFileMessage(sessionId: String, @SessionType sessionType: Int, receiverId: String, attachment: FileAttachment): IMMessage = Message(
        sessionId = sessionId, sessionType = sessionType,
        senderId = ImSdkImpl.getAccount() ?: "", receiverId = receiverId,
        type = FileAttachment.TYPE_FILE, direct = MsgDirection.OUT, status = MsgStatus.SENDING,
        time = System.currentTimeMillis(), attachment = attachment.toJson(false)
    )

    /** 创建红包消息 */
    fun createRedPacketMessage(
        sessionId: String,
        @SessionType sessionType: Int,
        receiverId: String,
        attachment: RedPacketAttachment
    ): IMMessage = Message(
        sessionId = sessionId,
        sessionType = sessionType,
        senderId = ImSdkImpl.getAccount() ?: "",
        receiverId = receiverId,
        type = MsgType.RED_PACKET,
        direct = MsgDirection.OUT,
        status = MsgStatus.SENDING,
        time = System.currentTimeMillis(),
        attachment = attachment.toJson(true)
    )

    /** 创建个人名片消息 */
    fun createCardMessage(
        sessionId: String,
        @SessionType sessionType: Int,
        receiverId: String,
        accountId: String,
        nickname: String,
        avatar: String
    ): IMMessage = Message(
        sessionId = sessionId,
        sessionType = sessionType,
        senderId = ImSdkImpl.getAccount() ?: "",
        receiverId = receiverId,
        type = com.kora.imui.attachment.CardAttachment.TYPE_CARD,
        direct = MsgDirection.OUT,
        status = MsgStatus.SENDING,
        time = System.currentTimeMillis(),
        attachment = com.kora.imui.attachment.CardAttachment().apply {
            this.accountId = accountId
            this.nickname = nickname
            this.avatar = avatar
        }.toJson(true)
    )

    /** 创建位置消息 */
    fun createLocationMessage(
        sessionId: String,
        @SessionType sessionType: Int,
        receiverId: String,
        latitude: Double,
        longitude: Double,
        title: String,
        address: String,
        snapshotPath: String
    ): IMMessage = Message(
        sessionId = sessionId,
        sessionType = sessionType,
        senderId = ImSdkImpl.getAccount() ?: "",
        receiverId = receiverId,
        type = com.kora.imui.attachment.LocationAttachment.TYPE_LOCATION,
        direct = MsgDirection.OUT,
        status = MsgStatus.SENDING,
        time = System.currentTimeMillis(),
        attachment = com.kora.imui.attachment.LocationAttachment().apply {
            this.latitude = latitude
            this.longitude = longitude
            this.title = title
            this.address = address
            this.snapshotPath = snapshotPath
        }.toJson(false)
    )

    /** 创建视频消息 */
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

    /** 创建文本消息 */
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

    /** 创建居中提示/通知消息（如领取红包提示） */
    fun createTipMessage(
        sessionId: String,
        @SessionType sessionType: Int = SessionType.None,
        receiverId: String = sessionId,
        msg: String = "",
        @MsgDirection msgDirect: Int = MsgDirection.IN
    ): IMMessage {
        return Message(
            sessionId = sessionId,
            sessionType = sessionType,
            type = MsgType.TIP,
            direct = msgDirect,
            status = MsgStatus.SUCCESS,
            senderId = ImSdkImpl.getAccount() ?: "",
            receiverId = receiverId,
            time = System.currentTimeMillis(),
            attachment = com.kora.imui.attachment.TipAttachment().apply { content = msg }.toJson(false)
        )
    }

    /** 创建图片消息 */
    fun createImageMessage(
        sessionId: String,
        @SessionType sessionType: Int = SessionType.None,
        @MsgType msgType: Int = MsgType.IMAGE,
        @MsgDirection msgDirect: Int = MsgDirection.OUT,
        @MsgStatus msgStatus: Int = MsgStatus.SENDING,
        receiverId: String = sessionId,
        localPath: String = "",
        mWidth: Int = 0,
        mHeight: Int = 0,
        size: Long = 0L,
        mimeType: String = ""
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
                this.localPath = localPath
                width = mWidth
                height = mHeight
                this.size = size
                this.mimeType = mimeType
            }.toJson(false)
        )
    }
}
