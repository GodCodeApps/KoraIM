package com.kora.imui

import com.kora.imui.listener.SessionEventListener

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/12/21:11:27
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