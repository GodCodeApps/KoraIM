package com.kora.imcore.netty

import com.google.gson.Gson
import com.kora.imcore.db.Message

/**
 * 网络传输的通信信封（Wire Protocol），定义了客户端与服务器之间的 JSON 帧格式。
 *
 * 传输格式为 NDJSON（Newline-Delimited JSON），每个帧以换行符结尾。
 *
 * 帧类型说明：
 * | 类型 | 方向 | 说明 |
 * |------|------|------|
 * | message | 双向 | 聊天消息，携带 [payload] |
 * | ack | 双向 | 消息确认，携带 [messageId] 和 [success] |
 * | login | C→S | 登录认证，携带 [account] |
 * | sync | C→S | 请求增量同步，携带起始 [cursor] |
 * | sync_result | S→C | 同步响应，携带 [events] 列表 |
 * | sync_ack | C→S | 同步确认，携带已处理的 [cursor] |
 * | ping | C→S | 心跳探测 |
 * | pong | S→C | 心跳响应 |
 */
internal data class WireEnvelope(
    /** 帧类型，取值见 TYPE_* 常量 */
    val type: String,
    /** 消息 ID，用于 ACK 匹配 */
    val messageId: String = "",
    /** 消息体，仅 message 类型帧携带 */
    val payload: Message? = null,
    /** 用户账号，仅 login 帧携带 */
    val account: String? = null,
    /** 会话 ID，ACK 帧中由服务端返回（首次会话由服务端分配） */
    val sessionId: String? = null,
    /** ACK 是否成功 */
    val success: Boolean? = null,
    /** 同步游标，用于增量同步定位 */
    val cursor: Long? = null,
    /** 服务端返回的下一个同步游标 */
    val nextCursor: Long? = null,
    /** 是否还有更多同步数据需要拉取 */
    val hasMore: Boolean? = null,
    /** 同步事件列表 */
    val events: List<SyncEvent>? = null,
    /** 接收方账号（用于 typing 等点对点控制信令） */
    val receiverId: String? = null,
    /** 发送方账号（用于 typing 等点对点控制信令） */
    val senderId: String? = null
) {
    /** 序列化为 JSON 并追加换行符（NDJSON 格式） */
    fun encode(gson: Gson): String = gson.toJson(this) + "\n"

    companion object {
        const val TYPE_MESSAGE = "message"
        const val TYPE_ACK = "ack"
        const val TYPE_LOGIN = "login"
        const val TYPE_SYNC = "sync"
        const val TYPE_SYNC_RESULT = "sync_result"
        const val TYPE_SYNC_ACK = "sync_ack"
        const val TYPE_PING = "ping"
        const val TYPE_PONG = "pong"
        const val TYPE_TYPING = "typing"

        /** 构建消息帧 */
        fun message(message: Message) = WireEnvelope(TYPE_MESSAGE, message.messageId, message)

        /** 构建登录帧 */
        fun login(account: String) = WireEnvelope(type = TYPE_LOGIN, account = account)

        /** 构建“正在输入”控制帧 */
        fun typing(receiverId: String) = WireEnvelope(type = TYPE_TYPING, receiverId = receiverId)

        /** 构建同步请求帧 */
        fun sync(cursor: Long) = WireEnvelope(type = TYPE_SYNC, cursor = cursor)

        /** 构建同步确认帧 */
        fun syncAck(cursor: Long) = WireEnvelope(type = TYPE_SYNC_ACK, cursor = cursor)

        /** 构建心跳探测帧 */
        fun ping() = WireEnvelope(type = TYPE_PING)

        /** 构建心跳响应帧 */
        fun pong() = WireEnvelope(type = TYPE_PONG)
    }
}

/**
 * 增量同步事件，表示一条离线期间发生的变更。
 *
 * @property cursor 事件对应的同步游标
 * @property eventType 事件类型（如 "message"）
 * @property payload 事件关联的消息体
 */
internal data class SyncEvent(
    val cursor: Long,
    val eventType: String,
    val payload: Message?
)
