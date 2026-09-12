package com.kora.imcore.netty

import android.content.Context
import com.kora.imcore.R
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.handler.codec.LineBasedFrameDecoder
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.CharsetUtil
import java.util.concurrent.TimeUnit

/**
 * Netty Channel Pipeline 初始化器，配置从 TCP 字节流到业务逻辑的完整处理链。
 *
 * Pipeline 结构（从底层到顶层）：
 * ```
 * TCP 字节流
 *   ↓ LineBasedFrameDecoder  — 按换行符切分帧（协议使用 NDJSON 格式）
 *   ↓ StringDecoder          — 字节 → UTF-8 字符串
 *   ↓ StringEncoder          — UTF-8 字符串 → 字节（出站方向）
 *   ↓ IdleStateHandler       — 心跳空闲检测（读空闲/写空闲触发事件）
 *   ↓ ChatClientHandler      — 业务帧处理（消息/ACK/同步/心跳）
 * ```
 *
 * @param onDisconnected 连接断开时的回调，传递给 [ChatClientHandler]
 */
class ChatClientInitializer(
    private val context: Context,
    private val onDisconnected: () -> Unit,
    private val tlsEnabled: Boolean,
    private val wireLogEnabled: Boolean
) : ChannelInitializer<SocketChannel>() {

    /** 读空闲超时（秒），超过此时间没收到任何数据则触发 READER_IDLE 事件 */
    @Volatile
    var readIdleSeconds: Long = DEFAULT_READ_IDLE_SECONDS

    /** 写空闲超时（秒），超过此时间没发送任何数据则触发 WRITER_IDLE 事件 */
    @Volatile
    var writeIdleSeconds: Long = DEFAULT_WRITE_IDLE_SECONDS

    override fun initChannel(ch: SocketChannel?) {
        ch?.pipeline()?.apply {
            if (wireLogEnabled) {
                addLast("wireTransportLog", WireLoggingHandler(if (tlsEnabled) "TLS-CIPHER" else "RAW-WIRE"))
            }
            if (tlsEnabled) {
                val sslContext = context.resources.openRawResource(R.raw.kora_im_dev_cert).use { certificate ->
                    SslContextBuilder.forClient().trustManager(certificate).build()
                }
                // The bundled certificate is a development certificate. Trust is pinned to it,
                // so the test server can be moved to another LAN IP without hostname changes.
                addLast("ssl", sslContext.newHandler(ch.alloc()))
                if (wireLogEnabled) {
                    addLast("wirePlaintextLog", WireLoggingHandler("TLS-PLAINTEXT"))
                }
            } else if (wireLogEnabled) {
                addLast("wirePlaintextLog", WireLoggingHandler("PLAINTEXT"))
            }
            // 1. 帧解码器：按换行符分割，单帧最大 1MB
            addLast("frameDecoder", LineBasedFrameDecoder(1024 * 1024))
            // 2. 字符串编解码
            addLast("decoder", StringDecoder(CharsetUtil.UTF_8))
            addLast("encoder", StringEncoder(CharsetUtil.UTF_8))
            // 3. 心跳检测：读空闲和写空闲分别触发不同处理
            addLast("idle", IdleStateHandler(readIdleSeconds, writeIdleSeconds, 0, TimeUnit.SECONDS))
            // 4. 业务处理器
            addLast("handler", ChatClientHandler(onDisconnected))
        }
    }

    companion object {
        /** 默认读空闲超时 45 秒（连续 2 次 = 90 秒无响应则断开） */
        const val DEFAULT_READ_IDLE_SECONDS = 45L

        /** 默认写空闲超时 15 秒（自动发 ping 保活） */
        const val DEFAULT_WRITE_IDLE_SECONDS = 15L
    }
}
