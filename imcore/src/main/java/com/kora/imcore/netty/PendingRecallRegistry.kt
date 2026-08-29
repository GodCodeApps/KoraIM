package com.kora.imcore.netty

import java.util.concurrent.ConcurrentHashMap

internal object PendingRecallRegistry {
    data class Result(val success: Boolean, val errorCode: String = "", val errorMessage: String = "")
    private val callbacks = ConcurrentHashMap<String, (Result) -> Unit>()

    fun register(requestId: String, callback: (Result) -> Unit) {
        callbacks.put(requestId, callback)?.invoke(Result(false, "REPLACED", "请求已被替换"))
    }
    fun complete(requestId: String, result: Result) { callbacks.remove(requestId)?.invoke(result) }
    fun fail(requestId: String) = complete(requestId, Result(false, "NETWORK", "网络异常，请稍后重试"))
    fun failAll() = callbacks.keys.toList().forEach(::fail)
}
