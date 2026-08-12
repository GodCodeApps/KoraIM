package com.kora.imcore.netty

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2022/1/13:15:47
 * @Description:
 */
class ChatClientInitializer : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel?) {
        ch?.pipeline()?.apply {
            addLast("decoder", StringDecoder(CharsetUtil.UTF_8))
            addLast("encoder", StringEncoder(CharsetUtil.UTF_8))
            addLast("handler", ChatClientHandler())
//     addLast(IdleStateHandler(30, 10, 0))//心跳
        }

    }
}