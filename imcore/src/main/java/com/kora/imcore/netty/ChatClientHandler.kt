package com.kora.imcore.netty

import android.util.Log
import com.google.gson.Gson
import com.kora.imcore.IMClient
import com.kora.imcore.constant.MsgDirection
import com.zchd.vsports.im.core.constant.MsgStatus
import com.kora.imcore.db.Message
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2022/1/13:15:47
 * @Description:
 */
class ChatClientHandler : ChannelInboundHandlerAdapter() {

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        super.channelInactive(ctx)
        Log.e("IWebSocketListener", "channelInactive")

    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        super.channelActive(ctx)
        Log.e("IWebSocketListener", "channelActive>>")

    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        super.channelRead(ctx, msg)
        val value = msg.toString()
        Log.e("IWebSocketListener", "channelRead>>${msg}")
        var message = Gson().fromJson(value, Message::class.java)
        message.id = 0 // 清除服务器回传的本地主键ID，防止数据库冲突
        message.status = MsgStatus.SUCCESS
        if (message.direct == MsgDirection.IN) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                IMClient.getReceiveListener()?.forEach {
                    it?.invoke(message)
                }
            }
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                IMClient.getMessageChangeListener()?.invoke(message)
            }
        }
        IMClient.updateMessageToLocal(message)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
        super.channelReadComplete(ctx)
        Log.e("IWebSocketListener", "channelReadComplete>>")
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        super.userEventTriggered(ctx, evt)
        if (evt is IdleStateEvent) {
            val idleStateEvent = evt
            if (idleStateEvent.state() == IdleState.WRITER_IDLE) {
                //写超时，此时可以发送心跳数据给服务器
                Log.e("IWebSocketListener", "userEventTriggered write idle")
            } else if (idleStateEvent.state() == IdleState.READER_IDLE) {
                //读超时，此时代表没有收到心跳返回可以关闭当前连接进行重连
                Log.e("IWebSocketListener", "userEventTriggered read idle")
                ctx?.channel()?.close()
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        super.exceptionCaught(ctx, cause)
        cause?.printStackTrace()
        ctx?.close()
    }
}