package com.kora.imcore.netty

import com.google.gson.Gson
import com.kora.imcore.db.Message

internal data class WireEnvelope(
    val type: String,
    val messageId: String,
    val payload: Message? = null
) {
    fun encode(gson: Gson): String = gson.toJson(this) + "\n"

    companion object {
        const val TYPE_MESSAGE = "message"
        const val TYPE_ACK = "ack"

        fun message(message: Message) = WireEnvelope(TYPE_MESSAGE, message.messageId, message)
    }
}
