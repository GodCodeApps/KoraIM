package com.kora.imcore.netty

import java.util.concurrent.ConcurrentHashMap

/**
 * 消息 ACK（确认）注册表，管理所有待确认的消息回调。
 *
 * 工作流程：
 * 1. 消息发送时调用 [register] 注册回调
 * 2. 收到服务端 ACK 时调用 [acknowledge]，触发成功回调
 * 3. 发送失败或超时时调用 [fail]，触发失败回调
 * 4. 连接断开时调用 [failAll]，批量触发所有等待中的回调
 *
 * 线程安全：内部使用 [ConcurrentHashMap]，可安全地从 Netty 线程和主线程并发访问。
 */
internal object PendingAckRegistry {

    /** ACK 结果 */
    data class Result(
        /** 是否成功 */
        val success: Boolean,
        /** 服务端分配的会话 ID（首次会话时由服务端生成） */
        val sessionId: String? = null
    )

    /** messageId → 回调函数 */
    private val callbacks = ConcurrentHashMap<String, (Result) -> Unit>()

    /**
     * 注册消息的 ACK 回调。
     * 如果该 messageId 已有注册（说明重复发送），会先以失败触发旧回调。
     */
    fun register(messageId: String, callback: (Result) -> Unit) {
        callbacks.put(messageId, callback)?.invoke(Result(false))
    }

    /** 消息确认成功，触发回调并移除注册 */
    fun acknowledge(messageId: String, sessionId: String?) {
        callbacks.remove(messageId)?.invoke(Result(true, sessionId))
    }

    /** 消息确认失败（超时/网络错误），触发回调并移除注册 */
    fun fail(messageId: String) {
        callbacks.remove(messageId)?.invoke(Result(false))
    }

    /** 将所有等待中的消息标记为失败（连接断开时调用） */
    fun failAll() {
        callbacks.keys.toList().forEach(::fail)
    }
}
