package com.kora.imcore

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Binder
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.kora.imcore.connection.NetworkMonitor
import com.kora.imcore.constant.MsgStatus
import com.kora.imcore.db.Message
import com.kora.imcore.netty.ChatClientInitializer
import com.kora.imcore.netty.PendingAckRegistry
import com.kora.imcore.netty.PendingRecallRegistry
import com.kora.imcore.netty.WireEnvelope
import com.kora.imcore.event.ConnectionState
import com.kora.imcore.event.IMEventHub
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * IM 核心后台 Service，负责管理 TCP 长连接的完整生命周期。
 *
 * 运行在应用进程内（非独立进程），通过 [LocalBinder] 与 [ImServiceProxy] 通信。
 *
 * 核心职责：
 * - **连接管理**：使用 Netty 建立和维护到 IM 服务器的 TCP 长连接
 * - **心跳保活**：通过 [ChatClientInitializer] 配置 IdleStateHandler，自动发送 ping/pong
 * - **断线重连**：指数退避 + 随机抖动，最多重试 [MAX_RECONNECT_ATTEMPTS] 次
 * - **网络感知**：监听系统网络变化，网络恢复时立即重连，网络断开时暂停重连
 * - **消息发送**：维护发送队列，逐条发送并等待 ACK 确认
 *
 * 连接建立后会自动执行：
 * 1. 发送 login 帧进行身份认证
 * 2. 发送 sync 帧拉取离线消息
 * 3. 排空待发送消息队列
 */
class IMService : Service() {
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Netty 事件循环组，单线程即可满足 IM 客户端需求 */
    private val eventLoopGroup = NioEventLoopGroup(1)

    /** 防止并发触发多次连接 */
    private val connecting = AtomicBoolean(false)

    // ---- 以下字段被 Main 线程和 Netty 线程共同访问，需要 @Volatile 保证可见性 ----
    @Volatile private var channel: Channel? = null
    @Volatile private var host = ""
    @Volatile private var port = 0
    @Volatile private var account = ""
    @Volatile private var syncCursor = 0L
    @Volatile private var reconnectAttempt = 0
    @Volatile private var released = false

    /** 待发送消息队列，线程安全 */
    private val outgoingMessages = ConcurrentLinkedQueue<Message>()

    /** 当前正在等待 ACK 的消息，同一时刻只有一条消息在"飞行中" */
    @Volatile private var inFlightMessage: Message? = null
    private val retryCountMap = ConcurrentLinkedQueue<RetryRecord>()

    /** 系统网络状态监听器 */
    private var networkMonitor: NetworkMonitor? = null

    /** 当前已调度的重连定时任务，网络恢复时需要取消它以立即重连 */
    private var pendingReconnectTask: java.util.concurrent.ScheduledFuture<*>? = null

    inner class LocalBinder : Binder() { val service: IMService get() = this@IMService }
    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * 建立到服务器的连接。
     * 由 [ImServiceProxy.onServiceConnected] 调用，传入服务器配置和同步游标。
     */
    internal fun connect(host: String, port: Int, account: String, syncCursor: Long) {
        this.host = host
        this.port = port
        this.account = account
        this.syncCursor = syncCursor
        released = false

        // 注册系统网络变化监听，用于在网络恢复时立即重连
        if (networkMonitor == null) {
            networkMonitor = NetworkMonitor(this).apply {
                onAvailable = { onNetworkAvailable() }
                onLost = { onNetworkLost() }
                register()
            }
        }

        connectIfNeeded()
    }

    /** 发送消息（JSON 字符串），由 [ImServiceProxy] 调用 */
    internal fun send(json: String) {
        val message = runCatching { gson.fromJson(json, Message::class.java) }.getOrNull() ?: return
        sendWithAck(message)
    }

    /** 发送“正在输入”实时控制帧，直接发送不走持久化队列与 ACK */
    internal fun sendTyping(receiverId: String) {
        val activeChannel = channel?.takeIf { it.isActive } ?: return
        activeChannel.writeAndFlush(WireEnvelope.typing(receiverId).encode(gson))
    }

    internal fun recall(messageId: String, requestId: String, callback: (PendingRecallRegistry.Result) -> Unit) {
        val activeChannel = channel?.takeIf { it.isActive }
        if (activeChannel == null) {
            callback(PendingRecallRegistry.Result(false, "NETWORK", "当前网络不可用"))
            return
        }
        val timeout = Runnable { PendingRecallRegistry.fail(requestId) }
        PendingRecallRegistry.register(requestId) { result ->
            mainHandler.removeCallbacks(timeout)
            callback(result)
        }
        mainHandler.postDelayed(timeout, ACK_TIMEOUT_MS)
        activeChannel.writeAndFlush(WireEnvelope.recall(messageId, requestId).encode(gson)).addListener { future ->
            if (!future.isSuccess) PendingRecallRegistry.fail(requestId)
        }
    }

    /**
     * 主动断开连接并释放资源。
     * 会清空消息队列、取消所有等待中的 ACK 回调、注销网络监听。
     */
    internal fun disconnect() {
        released = true
        cancelPendingReconnect()
        channel?.close()
        channel = null
        outgoingMessages.clear()
        retryCountMap.clear()
        PendingAckRegistry.failAll()
        PendingRecallRegistry.failAll()
        networkMonitor?.unregister()
        networkMonitor = null
        IMEventHub.setConnectionState(ConnectionState.Disconnected)
    }

    // ==================== 网络感知 ====================

    /**
     * 网络恢复回调。
     * 取消当前的退避定时器，重置重连计数，立即尝试重连。
     * 这样用户从地铁出来、WiFi 重连等场景能最快恢复连接。
     */
    private fun onNetworkAvailable() {
        if (released) return
        Log.i(TAG, "Network restored, attempting immediate reconnect")
        cancelPendingReconnect()
        reconnectAttempt = 0
        connectIfNeeded()
    }

    /**
     * 网络断开回调。
     * 主动关闭 TCP 连接并暂停重连定时器，避免在无网络时做无意义的重试。
     * 等 [onNetworkAvailable] 回调再恢复。
     */
    private fun onNetworkLost() {
        if (released) return
        Log.i(TAG, "Network lost, pausing reconnect")
        cancelPendingReconnect()
        channel?.close()
        channel = null
        IMEventHub.setConnectionState(ConnectionState.Disconnected)
    }

    // ==================== 连接管理 ====================

    /**
     * 按需建立连接。
     * - 如果已有活跃连接，直接排空消息队列
     * - 如果网络不可用，跳过（等网络恢复回调）
     * - 使用 [AtomicBoolean] 防止并发触发多次连接
     */
    private fun connectIfNeeded() {
        val activeChannel = channel
        if (activeChannel?.isActive == true) {
            drainOutgoingMessages()
            return
        }
        if (host.isBlank() || port !in 1..65535 || !connecting.compareAndSet(false, true)) return

        // 没有网络就不尝试连接，避免浪费资源
        if (networkMonitor?.isAvailable == false) {
            connecting.set(false)
            Log.d(TAG, "Network unavailable, skipping connect")
            IMEventHub.setConnectionState(ConnectionState.Disconnected)
            return
        }

        IMEventHub.setConnectionState(ConnectionState.Connecting)

        val bootstrap = Bootstrap()
            .group(eventLoopGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.TCP_NODELAY, true)     // 禁用 Nagle 算法，降低消息延迟
            .option(ChannelOption.SO_KEEPALIVE, true)     // 启用 TCP 层 keepalive 作为兜底
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
            .handler(ChatClientInitializer(::scheduleReconnect))

        bootstrap.connect(InetSocketAddress(host, port)).addListener { future ->
            connecting.set(false)
            if (future.isSuccess) {
                channel = (future as io.netty.channel.ChannelFuture).channel()
                reconnectAttempt = 0
                Log.i(TAG, "Connected to $host:$port")
                IMEventHub.setConnectionState(ConnectionState.Connected(host, port))
                // 连接成功后：1.登录认证 → 2.拉取离线消息 → 3.发送队列中的消息
                channel?.writeAndFlush(WireEnvelope.login(account).encode(gson))
                Log.i("KoraIM_Sync", "request account=$account cursor=${IMRuntime.syncCursor} reason=connected")
                channel?.writeAndFlush(WireEnvelope.sync(IMRuntime.syncCursor).encode(gson))
                drainOutgoingMessages()
            } else {
                Log.w(TAG, "Connection failed", future.cause())
                IMEventHub.setConnectionState(ConnectionState.Failed(future.cause()?.message ?: "Connection failed"))
                scheduleReconnect()
            }
        }
    }

    /**
     * 调度一次延迟重连。
     *
     * 策略：
     * - 指数退避：1s → 2s → 4s → 8s → 16s → 32s
     * - 随机抖动：在基础延迟上叠加 ±20%，防止大量客户端同时重连（惊群效应）
     * - 最大重试次数：[MAX_RECONNECT_ATTEMPTS] 次后放弃，进入 [ConnectionState.Failed]
     * - 网络感知：无网络时不调度，等 [onNetworkAvailable] 回调
     */
    private fun scheduleReconnect() {
        channel = null
        if (released || eventLoopGroup.isShuttingDown) return

        // 没有网络就不重连，等 NetworkMonitor 的 onAvailable 回调再触发
        if (networkMonitor?.isAvailable == false) {
            Log.d(TAG, "Network unavailable, waiting for network restore")
            IMEventHub.setConnectionState(ConnectionState.Disconnected)
            return
        }

        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached")
            IMEventHub.setConnectionState(ConnectionState.Failed("Max reconnect attempts reached"))
            return
        }

        val baseDelay = (1L shl reconnectAttempt.coerceAtMost(5))  // 1, 2, 4, 8, 16, 32 秒
        val jitter = (baseDelay * 0.2 * Math.random()).toLong()     // 随机抖动，防惊群
        val delaySeconds = baseDelay + jitter

        // 通知 UI 层当前正在重连中，附带重连次数和等待时间
        IMEventHub.setConnectionState(ConnectionState.Reconnecting(reconnectAttempt + 1, delaySeconds))
        Log.i(TAG, "Reconnecting in ${delaySeconds}s (attempt ${reconnectAttempt + 1}/$MAX_RECONNECT_ATTEMPTS)")

        reconnectAttempt++
        cancelPendingReconnect()
        pendingReconnectTask = eventLoopGroup.schedule({ connectIfNeeded() }, delaySeconds, TimeUnit.SECONDS)
    }

    /** 取消当前已调度的重连任务（网络恢复时需要取消旧定时器以立即重连） */
    private fun cancelPendingReconnect() {
        pendingReconnectTask?.cancel(false)
        pendingReconnectTask = null
    }

    // ==================== 消息发送 ====================

    /**
     * 带 ACK 确认的消息发送。
     *
     * 流程：
     * 1. 注册 ACK 回调到 [PendingAckRegistry]
     * 2. 设置 [ACK_TIMEOUT_MS] 超时定时器
     * 3. 消息入队 → 触发 [drainOutgoingMessages] 逐条发送
     * 4. 收到服务端 ACK → 回调更新消息状态（SUCCESS/FAIL）
     * 5. 超时未收到 ACK → 标记消息 FAIL
     *
     * ACK 回调中还会处理 sessionId 回填：
     * 首条消息发送时 sessionId 可能为空（服务端分配），收到 ACK 后会
     * 将 sessionId 回填到队列中同一接收者的待发消息上。
     */
    private fun sendWithAck(message: Message) {
        val timeout = Runnable {
            outgoingMessages.remove(message)
            PendingAckRegistry.fail(message.messageId)
        }
        PendingAckRegistry.register(message.messageId) { result ->
            mainHandler.removeCallbacks(timeout)
            if (result.success && !result.sessionId.isNullOrBlank()) {
                // 服务端分配了 sessionId，回填到当前消息和队列中同接收者的待发消息
                message.sessionId = result.sessionId
                outgoingMessages.forEach { queued ->
                    if (queued.sessionId.isBlank() && queued.receiverId == message.receiverId) {
                        queued.sessionId = result.sessionId
                    }
                }
            }
            message.status = if (result.success) MsgStatus.SUCCESS else MsgStatus.FAIL
            IMRuntime.updated(message)
            completeOutgoing(message)
        }
        mainHandler.postDelayed(timeout, ACK_TIMEOUT_MS)
        outgoingMessages.offer(message)
        connectIfNeeded()
    }

    /**
     * 从队列中取出下一条消息并发送。
     * 同一时刻只有一条消息在"飞行中"（等待 ACK），保证消息顺序和服务端处理能力。
     */
    @Synchronized
    private fun drainOutgoingMessages() {
        if (inFlightMessage != null) return       // 还有消息在等 ACK，不发下一条
        val activeChannel = channel?.takeIf { it.isActive } ?: return
        val message = outgoingMessages.poll() ?: return
        inFlightMessage = message
        activeChannel.writeAndFlush(WireEnvelope.message(message).encode(gson)).addListener { future ->
            if (!future.isSuccess) PendingAckRegistry.fail(message.messageId)
        }
    }

    /** 当前飞行中的消息完成（收到 ACK 或超时），清除标记并尝试发送下一条 */
    @Synchronized
    private fun completeOutgoing(message: Message) {
        if (inFlightMessage?.messageId == message.messageId) inFlightMessage = null
        if (!released) drainOutgoingMessages()
    }

    // ==================== 生命周期 ====================

    override fun onDestroy() {
        released = true
        cancelPendingReconnect()
        mainHandler.removeCallbacksAndMessages(null)
        PendingAckRegistry.failAll()
        PendingRecallRegistry.failAll()
        outgoingMessages.clear()
        retryCountMap.clear()
        inFlightMessage = null
        channel?.close()
        networkMonitor?.unregister()
        networkMonitor = null
        eventLoopGroup.shutdownGracefully()
        super.onDestroy()
    }

    private data class RetryRecord(val messageId: String, var count: Int)

    private companion object {
        const val TAG = "KoraIM"

        /** 消息 ACK 超时时间（毫秒），超时后消息标记为发送失败 */
        const val ACK_TIMEOUT_MS = 10_000L

        /** 最大重连次数，超过后不再自动重连，进入 Failed 状态 */
        const val MAX_RECONNECT_ATTEMPTS = 15
    }
}
