package com.kora.imcore

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.kora.imcore.event.ConnectionState
import com.kora.imcore.event.IMEventHub
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 同进程的 Service 连接代理，实现 [ServiceConnection] 接口。
 *
 * 作用：在 [ConnectionManager] 和 [IMService] 之间充当桥梁。
 * 由于 IMService 运行在同一进程（android:exported="false"），
 * 通过 [IMService.LocalBinder] 直接获取 Service 实例，无 IPC 开销。
 *
 * 消息缓冲：
 * 如果 Service 还未绑定完成就有消息要发送，消息会暂存在 [pendingMessages] 队列中，
 * 等 [onServiceConnected] 回调后自动排空发送。
 */
internal class ImServiceProxy : ServiceConnection {
    private var service: IMService? = null
    private var host = ""
    private var port = 0
    private var account = ""
    private var syncCursor = 0L

    /** 待发送消息缓冲队列（Service 绑定前的消息暂存在此） */
    private val pendingMessages = ConcurrentLinkedQueue<String>()

    /** 设置服务器连接参数（在 bindService 之前调用） */
    fun setServerConfig(host: String, port: Int, account: String, syncCursor: Long) {
        this.host = host
        this.port = port
        this.account = account
        this.syncCursor = syncCursor
    }

    /** 发送消息：Service 已就绪则直接发送，否则入队等待 */
    fun sendMessage(message: String) {
        service?.send(message) ?: pendingMessages.offer(message)
    }

    /** 发送正在输入控制信令 */
    fun sendTyping(receiverId: String) {
        service?.sendTyping(receiverId)
    }
    fun sendCallSignal(signal: com.kora.imcore.call.CallSignal): Boolean = service?.sendCallSignal(signal) == true

    fun recall(messageId: String, requestId: String, callback: (com.kora.imcore.netty.PendingRecallRegistry.Result) -> Unit) {
        service?.recall(messageId, requestId, callback)
            ?: callback(com.kora.imcore.netty.PendingRecallRegistry.Result(false, "NETWORK", "服务尚未连接"))
    }

    /** 断开连接并清空状态 */
    fun disconnect() {
        service?.disconnect()
        service = null
        pendingMessages.clear()
        IMEventHub.setConnectionState(ConnectionState.Disconnected)
    }

    /** Service 绑定成功：获取 Service 实例 → 建立 TCP 连接 → 排空缓冲队列 */
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = (binder as? IMService.LocalBinder)?.service
        val connectedService = service ?: return
        connectedService.connect(host, port, account, syncCursor)
        // 排空绑定前缓冲的消息
        while (true) connectedService.send(pendingMessages.poll() ?: break)
    }

    /** Service 意外断开（通常不会发生，因为是同进程绑定） */
    override fun onServiceDisconnected(name: ComponentName?) {
        Log.w(TAG, "IM service disconnected")
        service = null
        IMEventHub.setConnectionState(ConnectionState.Disconnected)
    }

    private companion object { const val TAG = "KoraIM" }
}
