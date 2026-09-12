package com.kora.imcore.netty

import android.util.Log
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.util.CharsetUtil

/** Test-only logger placed before and after SslHandler. */
internal class WireLoggingHandler(private val label: String) : ChannelDuplexHandler() {

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        log("IN", msg)
        ctx.fireChannelRead(msg)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        log("OUT", msg)
        ctx.write(msg, promise)
    }

    private fun log(direction: String, msg: Any) {
        if (msg !is ByteBuf) return

        val length = msg.readableBytes()
        val loggedLength = minOf(length, MAX_LOG_BYTES)
        val value = if (label.contains("CIPHER")) {
            ByteBufUtil.hexDump(msg, msg.readerIndex(), loggedLength)
        } else {
            msg.toString(msg.readerIndex(), loggedLength, CharsetUtil.UTF_8)
                .replace("\r", "\\r")
                .replace("\n", "\\n")
        }
        val suffix = if (length > loggedLength) " truncated" else ""
        Log.d(TAG, "[$label][$direction] bytes=$length$suffix $value")
    }

    private companion object {
        const val TAG = "KoraIM_Wire"
        const val MAX_LOG_BYTES = 4096
    }
}
