package com.kora.imui.module

import androidx.fragment.app.FragmentActivity
import com.kora.imcore.impl.IMMessage

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/15:09:36
 * @Description:
 */
interface ModuleProxy {
    fun getAppActivity(): FragmentActivity

    fun sendMessage(msg: IMMessage): Boolean

    fun resendMessage(msg: IMMessage): Boolean {
        return true
    }
}
