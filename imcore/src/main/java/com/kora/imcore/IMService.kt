package com.kora.imcore

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Binder
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.kora.imcore.constant.MsgStatus
import com.kora.imcore.db.Message
import com.kora.imcore.netty.ChatClientInitializer
import com.kora.imcore.netty.PendingAckRegistry
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

class IMService : Service() {
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val eventLoopGroup = NioEventLoopGroup(1)
    private val connecting = AtomicBoolean(false)
    private var channel: Channel? = null
    private var host = ""
    private var port = 0
    private var account = ""
    private var syncCursor = 0L
    private var reconnectAttempt = 0
    private var released = false
    private val outgoingMessages = ConcurrentLinkedQueue<Message>()

    inner class LocalBinder : Binder() { val service: IMService get() = this@IMService }
    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    internal fun connect(host: String, port: Int, account: String, syncCursor: Long) {
        this.host = host
        this.port = port
        this.account = account
        this.syncCursor = syncCursor
        released = false
        connectIfNeeded()
    }

    internal fun send(json: String) {
        val message = runCatching { gson.fromJson(json, Message::class.java) }.getOrNull() ?: return
        sendWithAck(message)
    }

    internal fun disconnect() {
        released = true
        channel?.close()
        channel = null
        PendingAckRegistry.failAll()
        outgoingMessages.clear()
        IMEventHub.setConnectionState(ConnectionState.Disconnected)
    }

    private fun connectIfNeeded() {
        val activeChannel = channel
        if (activeChannel?.isActive == true) {
            drainOutgoingMessages()
            return
        }
        if (host.isBlank() || port !in 1..65535 || !connecting.compareAndSet(false, true)) return
        IMEventHub.setConnectionState(ConnectionState.Connecting)

        val bootstrap = Bootstrap()
            .group(eventLoopGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
            .handler(ChatClientInitializer(::scheduleReconnect))

        bootstrap.connect(InetSocketAddress(host, port)).addListener { future ->
            connecting.set(false)
            if (future.isSuccess) {
                channel = (future as io.netty.channel.ChannelFuture).channel()
                reconnectAttempt = 0
                Log.i(TAG, "Connected to $host:$port")
                IMEventHub.setConnectionState(ConnectionState.Connected(host, port))
                channel?.writeAndFlush(WireEnvelope.login(account).encode(gson))
                channel?.writeAndFlush(WireEnvelope.sync(IMRuntime.syncCursor).encode(gson))
                drainOutgoingMessages()
            } else {
                Log.w(TAG, "Connection failed", future.cause())
                IMEventHub.setConnectionState(ConnectionState.Failed(future.cause()?.message ?: "Connection failed"))
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        channel = null
        if (released || eventLoopGroup.isShuttingDown) return
        val delaySeconds = (1L shl reconnectAttempt.coerceAtMost(5))
        reconnectAttempt++
        eventLoopGroup.schedule({ connectIfNeeded() }, delaySeconds, TimeUnit.SECONDS)
    }

    private fun sendWithAck(message: Message) {
        val timeout = Runnable {
            outgoingMessages.remove(message)
            PendingAckRegistry.fail(message.messageId)
        }
        PendingAckRegistry.register(message.messageId) { result ->
            mainHandler.removeCallbacks(timeout)
            if (result.success && !result.sessionId.isNullOrBlank()) message.sessionId = result.sessionId
            message.status = if (result.success) MsgStatus.SUCCESS else MsgStatus.FAIL
            IMRuntime.updated(message)
        }
        mainHandler.postDelayed(timeout, ACK_TIMEOUT_MS)
        outgoingMessages.offer(message)
        connectIfNeeded()
    }

    private fun drainOutgoingMessages() {
        val activeChannel = channel?.takeIf { it.isActive } ?: return
        while (true) {
            val message = outgoingMessages.poll() ?: break
            activeChannel.writeAndFlush(WireEnvelope.message(message).encode(gson)).addListener { future ->
                if (!future.isSuccess) PendingAckRegistry.fail(message.messageId)
            }
        }
    }

    override fun onDestroy() {
        released = true
        mainHandler.removeCallbacksAndMessages(null)
        PendingAckRegistry.failAll()
        outgoingMessages.clear()
        channel?.close()
        eventLoopGroup.shutdownGracefully()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "KoraIM"
        const val ACK_TIMEOUT_MS = 10_000L
    }
}
