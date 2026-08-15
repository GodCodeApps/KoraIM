package com.kora.imui

import com.kora.imui.listener.SessionEventListener
import com.kora.imui.provider.IMMediaMessageProvider

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/21:11:27
 * @Description:
 */
object ImUIKitImpl {
    private var sessionEventListener: SessionEventListener? = null
    private var mediaMessageProvider: IMMediaMessageProvider? = null
    fun getSessionListener(): SessionEventListener? = sessionEventListener
    //聊天点击事件
    fun setSessionEventListener(listener: SessionEventListener?) {
        sessionEventListener = listener
    }
    fun setMediaMessageProvider(provider: IMMediaMessageProvider?) {
        mediaMessageProvider = provider
    }
    fun getMediaMessageProvider(): IMMediaMessageProvider? = mediaMessageProvider
}
