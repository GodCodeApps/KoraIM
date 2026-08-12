package com.kora.imui.module

import androidx.fragment.app.FragmentActivity
import com.kora.imcore.impl.IMMessage

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/12/15:09:36
 * @Description:
 */
interface ModuleProxy {
    fun getAppActivity(): FragmentActivity

    fun sendMessage(msg: IMMessage): Boolean

    fun resendMessage(msg: IMMessage): Boolean {
        return true
    }
}