package com.kora.imui

import com.kora.imui.listener.SessionEventListener

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/21:11:27
 * @Description:
 */
object ImUIKitImpl {
    private var mAccount: String? = null
    private var sessionEventListener: SessionEventListener? = null
    fun setAccount(account: String?) {
        mAccount = account
    }
    fun getAccount(): String? = mAccount
    fun getSessionListener(): SessionEventListener? = sessionEventListener
    fun setSessionEventListener(listener: SessionEventListener?) {
        sessionEventListener = listener
    }
}
