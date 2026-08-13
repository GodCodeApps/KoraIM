package com.kora.imcore.netty

import java.util.concurrent.ConcurrentHashMap

internal object PendingAckRegistry {
    private val callbacks = ConcurrentHashMap<String, (Boolean) -> Unit>()

    fun register(messageId: String, callback: (Boolean) -> Unit) {
        callbacks.put(messageId, callback)?.invoke(false)
    }

    fun acknowledge(messageId: String) {
        callbacks.remove(messageId)?.invoke(true)
    }

    fun fail(messageId: String) {
        callbacks.remove(messageId)?.invoke(false)
    }

    fun failAll() {
        callbacks.keys.toList().forEach(::fail)
    }
}
