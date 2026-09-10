package com.kora.imcore.listener

/**
 * 账号被挤下线监听器（Java 友好接口）。
 *
 * Kotlin 用户推荐使用 [com.kora.imcore.IMClient.kickEvents] Flow。
 * Java 用户推荐使用此监听器，通过 [com.kora.imcore.IMClient.addKickListener] 注册。
 */
fun interface KickListener {
    /**
     * 账号在其他设备登录被踢下线时回调（保证在 Android 主线程执行）。
     *
     * @param reason 被踢原因描述（如 "您的账号已在其他设备登录"）
     */
    fun onKicked(reason: String)
}

/**
 * 被踢下线订阅句柄，用于取消监听。
 *
 * 在不再需要监听时（如 Activity 销毁），调用 [cancel] 停止监听。
 * ```java
 * KickSubscription subscription = IMClient.addKickListener(reason -> {
 *     // 处理被踢下线逻辑
 * });
 * // 页面销毁时：
 * subscription.cancel();
 * ```
 */
class KickSubscription internal constructor(
    private val cancelAction: () -> Unit
) {
    /** 取消订阅，停止接收被踢下线事件 */
    fun cancel() = cancelAction()
}
