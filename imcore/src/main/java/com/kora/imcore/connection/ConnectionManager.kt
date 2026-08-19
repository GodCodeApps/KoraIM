package com.kora.imcore.connection

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.kora.imcore.IMService
import com.kora.imcore.ImServiceProxy
import com.kora.imcore.impl.IMMessage

/**
 * 连接管理器，封装了 Android Service 的绑定/解绑操作。
 *
 * 通过 [ImServiceProxy]（ServiceConnection）与 [IMService] 通信：
 * - [connect]：绑定 IMService，传入服务器配置
 * - [send]：将消息序列化为 JSON 后交给 proxy 发送
 * - [disconnect]：断开连接并解绑 Service
 *
 * 设计说明：
 * IMService 运行在同一进程内（非远程 Service），使用 LocalBinder 直接获取
 * Service 实例，无 AIDL 开销。bindService 的 BIND_AUTO_CREATE 标志确保
 * Service 在绑定期间保持存活。
 */
internal class ConnectionManager(private val context: Context) {
    private val gson = Gson()
    private val proxy = ImServiceProxy()
    private var bound = false

    /** 绑定 IMService 并建立到服务器的 TCP 连接 */
    fun connect(host: String, port: Int, account: String, syncCursor: Long) {
        disconnect()
        proxy.setServerConfig(host, port, account, syncCursor)
        bound = context.bindService(Intent(context, IMService::class.java), proxy, Context.BIND_AUTO_CREATE)
        check(bound) { "Unable to bind KoraIM service" }
    }

    /** 将消息序列化为 JSON 后通过 proxy 发送到 IMService */
    fun send(message: IMMessage) {
        check(bound) { "IMClient.init(context, host, port) must be called before sendMessage" }
        proxy.sendMessage(gson.toJson(message))
    }

    /** 发送“正在输入”控制信令 */
    fun sendTyping(receiverId: String) {
        if (bound) {
            proxy.sendTyping(receiverId)
        }
    }

    /** 断开连接并解绑 Service */
    fun disconnect() {
        proxy.disconnect()
        if (bound) runCatching { context.unbindService(proxy) }
        bound = false
    }
}
