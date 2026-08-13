package com.kora.imcore.netty

import com.google.gson.Gson
import com.kora.imcore.db.Message

internal data class WireEnvelope(
    val type: String,
    val messageId: String = "",
    val payload: Message? = null,
    val account: String? = null,
    val sessionId: String? = null,
    val success: Boolean? = null
) {
    fun encode(gson: Gson): String = gson.toJson(this) + "\n"

    companion object {
        const val TYPE_MESSAGE = "message"
        const val TYPE_ACK = "ack"
        const val TYPE_LOGIN = "login"

        fun message(message: Message) = WireEnvelope(TYPE_MESSAGE, message.messageId, message)
        fun login(account: String) = WireEnvelope(type = TYPE_LOGIN, account = account)
    }
}
