package com.kora.imcore.netty

import com.google.gson.Gson
import com.kora.imcore.db.Message

internal data class WireEnvelope(
    val type: String,
    val messageId: String = "",
    val payload: Message? = null,
    val account: String? = null,
    val sessionId: String? = null,
    val success: Boolean? = null,
    val cursor: Long? = null,
    val nextCursor: Long? = null,
    val hasMore: Boolean? = null,
    val events: List<SyncEvent>? = null
) {
    fun encode(gson: Gson): String = gson.toJson(this) + "\n"

    companion object {
        const val TYPE_MESSAGE = "message"
        const val TYPE_ACK = "ack"
        const val TYPE_LOGIN = "login"
        const val TYPE_SYNC = "sync"
        const val TYPE_SYNC_RESULT = "sync_result"
        const val TYPE_SYNC_ACK = "sync_ack"

        fun message(message: Message) = WireEnvelope(TYPE_MESSAGE, message.messageId, message)
        fun login(account: String) = WireEnvelope(type = TYPE_LOGIN, account = account)
        fun sync(cursor: Long) = WireEnvelope(type = TYPE_SYNC, cursor = cursor)
        fun syncAck(cursor: Long) = WireEnvelope(type = TYPE_SYNC_ACK, cursor = cursor)
    }
}

internal data class SyncEvent(
    val cursor: Long,
    val eventType: String,
    val payload: Message?
)
