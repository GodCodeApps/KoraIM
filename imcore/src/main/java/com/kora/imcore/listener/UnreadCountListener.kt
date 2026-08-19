package com.kora.imcore.listener

/**
 * 未读消息总数变化监听器（Java 友好接口）。
 *
 * Kotlin 用户推荐直接使用 [IMClient.observeTotalUnreadCount] Flow。
 * Java 用户使用此监听器，通过 [IMClient.addUnreadCountListener] 注册。
 */
fun interface UnreadCountListener {
    /** 未读总数变化时回调，[count] 为当前所有会话的未读消息总数 */
    fun onUnreadCountChanged(count: Int)
}

/**
 * 未读数订阅句柄，用于取消监听。
 *
 * 在不再需要未读数更新时（如 Activity 销毁），调用 [cancel] 停止监听。
 * ```kotlin
 * val subscription = IMClient.addUnreadCountListener { count -> updateBadge(count) }
 * // 不再需要时：
 * subscription.cancel()
 * ```
 */
class UnreadCountSubscription internal constructor(
    private val cancelAction: () -> Unit
) {
    /** 取消订阅，停止接收未读数更新 */
    fun cancel() = cancelAction()
}
