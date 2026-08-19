package com.kora.imcore.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/**
 * 系统网络状态监听器，实时感知设备网络连通性变化。
 *
 * 使用 [ConnectivityManager.NetworkCallback] 监听网络变化事件：
 * - 网络恢复时通过 [onAvailable] 回调通知 [IMService]，触发立即重连
 * - 网络断开时通过 [onLost] 回调通知 [IMService]，暂停无意义的重连定时器
 *
 * 注意事项：
 * - [onLost] 触发时会检查是否还有其他可用网络（如 WiFi 断了但 4G 还在），
 *   只有所有网络都断开才真正通知调用方
 * - 需要 ACCESS_NETWORK_STATE 权限
 */
internal class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** 当前网络是否可用（volatile 保证跨线程可见性） */
    @Volatile
    var isAvailable: Boolean = checkCurrent()
        private set

    /** 网络恢复回调，由 IMService 设置 */
    var onAvailable: (() -> Unit)? = null

    /** 网络断开回调，由 IMService 设置 */
    var onLost: (() -> Unit)? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!isAvailable) {
                Log.i(TAG, "Network available")
                isAvailable = true
                onAvailable?.invoke()
            }
        }

        override fun onLost(network: Network) {
            // 可能只是某一个网络断了（比如 WiFi），需要检查是否还有其他可用网络
            if (!checkCurrent()) {
                Log.i(TAG, "Network lost")
                isAvailable = false
                onLost?.invoke()
            }
        }
    }

    private var registered = false

    /** 注册网络状态监听（只监听具有 INTERNET 能力的网络） */
    fun register() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { connectivityManager.registerNetworkCallback(request, callback) }
        registered = true
    }

    /** 注销网络状态监听 */
    fun unregister() {
        if (!registered) return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        registered = false
    }

    /** 查询当前是否有可用的互联网连接 */
    private fun checkCurrent(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val TAG = "KoraIM"
    }
}
