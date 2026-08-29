package com.kora.imcore.netty

import android.util.Log
import com.google.gson.Gson
import com.kora.imcore.IMRuntime
import com.kora.imcore.constant.MsgStatus
import com.kora.imcore.db.Message
import com.kora.imcore.event.ConnectionState
import com.kora.imcore.event.IMEventHub
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent

/**
 * Netty 入站消息处理器，负责处理来自 IM 服务器的所有帧。
 *
 * 处理的帧类型：
 * - **ack**：服务端对客户端发送消息的确认，触发 [PendingAckRegistry] 回调
 * - **message**：服务端推送的新消息，回复 ack 后交给 [IMRuntime] 处理
 * - **sync_result**：增量同步响应，包含离线期间的消息事件
 * - **pong**：心跳响应，重置 [missedPongs] 计数
 *
 * 心跳机制（配合 [ChatClientInitializer] 中的 IdleStateHandler）：
 * - 写空闲 15s → 主动发 ping，保持连接活跃
 * - 读空闲 45s → 说明可能对端不可达，发 ping 探测
 * - 连续 [MAX_MISSED_PONGS] 次读空闲无响应 → 关闭连接触发重连
 *
 * @param onDisconnected 连接断开时的回调，通常指向 [IMService.scheduleReconnect]
 */
internal class ChatClientHandler(
    private val onDisconnected: () -> Unit
) : SimpleChannelInboundHandler<String>() {
    private val gson = Gson()

    /** 连续未收到 pong 响应的次数，每次收到任何服务端数据都会归零 */
    private var missedPongs = 0

    override fun channelRead0(ctx: ChannelHandlerContext, frame: String) {
        try {
            val envelope = gson.fromJson(frame, WireEnvelope::class.java)
            when (envelope.type) {
                WireEnvelope.TYPE_ACK -> {
                    // 服务端确认了客户端发送的消息
                    if (envelope.success == false) PendingAckRegistry.fail(envelope.messageId)
                    else PendingAckRegistry.acknowledge(envelope.messageId, envelope.sessionId)
                }
                WireEnvelope.TYPE_MESSAGE -> envelope.payload?.let { message ->
                    // 收到新消息：先回复 ACK 告诉服务端已收到，再分发到业务层
                    ctx.writeAndFlush(gson.toJson(WireEnvelope(WireEnvelope.TYPE_ACK, envelope.messageId)) + "\n")
                    deliverMessage(message)
                }
                WireEnvelope.TYPE_RECALL -> envelope.payload?.let(IMRuntime::recalled)
                WireEnvelope.TYPE_RECALL_ACK -> PendingRecallRegistry.complete(
                    envelope.requestId.orEmpty(),
                    PendingRecallRegistry.Result(
                        envelope.success == true,
                        envelope.errorCode.orEmpty(),
                        envelope.errorMessage.orEmpty()
                    )
                )
                WireEnvelope.TYPE_SYNC_RESULT -> {
                    // 增量同步响应：批量处理离线消息，更新同步游标
                    val events = envelope.events.orEmpty()
                    val nextCursor = envelope.nextCursor ?: 0L
                    Log.i(
                        "KoraIM_Sync",
                        "result events=${events.size} nextCursor=$nextCursor hasMore=${envelope.hasMore} " +
                            "items=${events.joinToString(limit = 20) { "${it.eventType}:${it.payload?.messageId.orEmpty()}@${it.cursor}" }}"
                    )
                    IMRuntime.synced(events, nextCursor) {
                        // 同步数据已落库，回复 sync_ack 告知服务端可以清理
                        Log.i("KoraIM_Sync", "ack cursor=$nextCursor afterCommit=true")
                        ctx.writeAndFlush(WireEnvelope.syncAck(nextCursor).encode(gson))
                        // 如果还有更多数据，继续拉取下一批
                        if (envelope.hasMore == true) {
                            Log.i("KoraIM_Sync", "request cursor=$nextCursor reason=hasMore")
                            ctx.writeAndFlush(WireEnvelope.sync(nextCursor).encode(gson))
                        }
                    }
                }
                WireEnvelope.TYPE_PONG -> {
                    // 收到心跳响应，重置计数
                    missedPongs = 0
                }
                WireEnvelope.TYPE_TYPING -> {
                    // 收到对方正在输入的实时信令
                    val senderId = envelope.senderId.orEmpty()
                    if (senderId.isNotBlank()) {
                        Log.d("KoraIM_Typing", "Received typing signal from sender: $senderId")
                        IMEventHub.emitTyping(senderId)
                    }
                }
                else -> Log.w(TAG, "Ignoring unknown frame type: ${envelope.type}")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Invalid server frame", error)
        }
    }

    /**
     * 处理 IdleStateHandler 触发的空闲事件。
     * 这是应用层心跳的核心逻辑，比 TCP keepalive 更及时地检测连接断开。
     */
    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            when (evt.state()) {
                IdleState.WRITER_IDLE -> {
                    // 写空闲：一段时间没有发送任何数据，主动发 ping 保持连接活跃
                    ctx.writeAndFlush(WireEnvelope.ping().encode(gson))
                }
                IdleState.READER_IDLE -> {
                    // 读空闲：一段时间没有收到任何数据（包括 pong），连接可能已断开
                    missedPongs++
                    if (missedPongs >= MAX_MISSED_PONGS) {
                        // 连续多次未收到响应，判定连接已死，关闭触发重连
                        Log.w(TAG, "No response after $MAX_MISSED_PONGS heartbeat cycles, closing connection")
                        ctx.close()
                    } else {
                        // 先再试一次 ping，给对端一次机会
                        Log.d(TAG, "Reader idle, missed pongs: $missedPongs, sending ping")
                        ctx.writeAndFlush(WireEnvelope.ping().encode(gson))
                    }
                }
                else -> {}
            }
        }
        super.userEventTriggered(ctx, evt)
    }

    /** 将收到的消息交给 [IMRuntime] 分发（落库 + 通知 UI） */
    private fun deliverMessage(message: Message) {
        message.id = 0                       // 清除主键，让本地数据库自动分配
        message.status = MsgStatus.SUCCESS   // 能收到就说明服务端已成功处理
        IMRuntime.incoming(message)
    }

    /** 连接建立时重置心跳计数 */
    override fun channelActive(ctx: ChannelHandlerContext) {
        missedPongs = 0
        super.channelActive(ctx)
    }

    /** 连接断开时通知 IMService 触发重连 */
    override fun channelInactive(ctx: ChannelHandlerContext) {
        Log.w(TAG, "Connection inactive")
        onDisconnected()
    }

    /** 连接异常时先通知 UI 层，再关闭连接（关闭后会触发 channelInactive → 重连） */
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.e(TAG, "Connection error", cause)
        IMEventHub.setConnectionState(ConnectionState.Failed(cause.message ?: "Connection error"))
        ctx.close()
    }

    private companion object {
        const val TAG = "KoraIM"

        /** 连续未收到 pong 响应的最大次数，超过后关闭连接触发重连 */
        const val MAX_MISSED_PONGS = 2
    }
}
