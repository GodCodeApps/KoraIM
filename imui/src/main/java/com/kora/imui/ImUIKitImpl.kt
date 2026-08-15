package com.kora.imui

import com.kora.imui.listener.SessionEventListener

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/21:11:27
 * @Description:
 */
object ImUIKitImpl {
    private var sessionEventListener: SessionEventListener? = null
    fun getSessionListener(): SessionEventListener? = sessionEventListener
    fun setSessionEventListener(listener: SessionEventListener?) {
        sessionEventListener = listener
    }
}
