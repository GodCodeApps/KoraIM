package com.kora.imcore.netty

import java.util.concurrent.ConcurrentHashMap

internal object PendingAckRegistry {
    data class Result(val success: Boolean, val sessionId: String? = null)

    private val callbacks = ConcurrentHashMap<String, (Result) -> Unit>()

    fun register(messageId: String, callback: (Result) -> Unit) {
        callbacks.put(messageId, callback)?.invoke(Result(false))
    }

    fun acknowledge(messageId: String, sessionId: String?) {
        callbacks.remove(messageId)?.invoke(Result(true, sessionId))
    }

    fun fail(messageId: String) {
        callbacks.remove(messageId)?.invoke(Result(false))
    }

    fun failAll() {
        callbacks.keys.toList().forEach(::fail)
    }
}
