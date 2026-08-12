package com.kora.imcore

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kora.imcore.aidl.ImAidlInterface
import com.kora.imcore.netty.ChatClientInitializer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.AdaptiveRecvByteBufAllocator
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.InetSocketAddress

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/12/16:17:27
 * @Description:
 */
class IMService : Service() {
    companion object {
        var TAG = "IWebSocketListener"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return ImIBinder()
    }

    class ImIBinder : ImAidlInterface.Stub() {
        var channelFuture: ChannelFuture? = null
        private var host = ""
        private var port = 0
        private var bootstrap: Bootstrap? = null
        override fun connect(host: String, port: Int) {
            this.host = host
            this.port = port
            GlobalScope.launch {
                try {
                    var group = NioEventLoopGroup()
                    bootstrap = Bootstrap()
                        .group(group)
                        .option(ChannelOption.TCP_NODELAY, true)//无阻塞
                        .option(ChannelOption.SO_KEEPALIVE, true)//长链接
                        .option(ChannelOption.SO_TIMEOUT, 3000)//收发超时
                        .option(
                            ChannelOption.RCVBUF_ALLOCATOR,
                            AdaptiveRecvByteBufAllocator(5000, 5000, 8000)
                        )
                        .channel(NioSocketChannel::class.java)
                        .handler(ChatClientInitializer())
                    connect()
                } catch (e: Exception) {
                }
            }
        }

        override fun send(msg: String?) {
            if (msg != null && msg.isNotEmpty()) {
                Log.d(TAG, "send${msg}")
                if (needReConnect()) {
                    connect {
                        channelFuture?.channel()?.writeAndFlush(msg)?.addListener {
                            Log.d(TAG, "send》》》${it.isSuccess}")
                        }
                    }
                } else {
                    channelFuture?.channel()?.writeAndFlush(msg)?.addListener {
                        Log.d(TAG, "send》》》${it.isSuccess}")
                    }

                }
            }
        }

        private fun needReConnect(): Boolean {
            return !(channelFuture != null && channelFuture?.channel()?.isActive == true)
        }

        private fun connect(cb: ((Boolean) -> Unit)? = null) {
            val inetSocketAddress = InetSocketAddress(host, port)
            channelFuture = bootstrap?.connect(inetSocketAddress)?.addListener {
                if (it.isSuccess) {
                    Log.d(TAG, "链接成功")
                    cb?.invoke(true)
                } else {
                    Log.d(TAG, "链接失败")
                    cb?.invoke(false)
                }
            }?.sync()
        }
    }
}