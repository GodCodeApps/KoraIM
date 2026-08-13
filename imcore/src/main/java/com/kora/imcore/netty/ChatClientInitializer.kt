package com.kora.imcore.netty

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.handler.codec.LineBasedFrameDecoder
import io.netty.util.CharsetUtil

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2022/1/13:15:47
 * @Description:
 */
class ChatClientInitializer(
    private val onDisconnected: () -> Unit
) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel?) {
        ch?.pipeline()?.apply {
            addLast("frameDecoder", LineBasedFrameDecoder(1024 * 1024))
            addLast("decoder", StringDecoder(CharsetUtil.UTF_8))
            addLast("encoder", StringEncoder(CharsetUtil.UTF_8))
            addLast("handler", ChatClientHandler(onDisconnected))
//     addLast(IdleStateHandler(30, 10, 0))//心跳
        }

    }
}
