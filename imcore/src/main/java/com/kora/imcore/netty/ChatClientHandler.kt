package com.kora.imcore.netty

import android.util.Log
import com.google.gson.Gson
import com.kora.imcore.IMRuntime
import com.kora.imcore.constant.MsgStatus
import com.kora.imcore.db.Message
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler

internal class ChatClientHandler(
    private val onDisconnected: () -> Unit
) : SimpleChannelInboundHandler<String>() {
    private val gson = Gson()

    override fun channelRead0(ctx: ChannelHandlerContext, frame: String) {
        try {
            val envelope = gson.fromJson(frame, WireEnvelope::class.java)
            when (envelope.type) {
                WireEnvelope.TYPE_ACK -> PendingAckRegistry.acknowledge(envelope.messageId)
                WireEnvelope.TYPE_MESSAGE -> envelope.payload?.let { message ->
                    ctx.writeAndFlush(gson.toJson(WireEnvelope(WireEnvelope.TYPE_ACK, envelope.messageId)) + "\n")
                    deliverMessage(message)
                }
                else -> Log.w(TAG, "Ignoring unknown frame type: ${envelope.type}")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Invalid server frame", error)
        }
    }

    private fun deliverMessage(message: Message) {
        message.id = 0
        message.status = MsgStatus.SUCCESS
        IMRuntime.incoming(message)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        Log.w(TAG, "Connection inactive")
        onDisconnected()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.e(TAG, "Connection error", cause)
        ctx.close()
    }

    private companion object {
        const val TAG = "KoraIM"
    }
}
