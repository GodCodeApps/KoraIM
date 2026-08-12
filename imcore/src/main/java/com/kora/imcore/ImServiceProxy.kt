package com.kora.imcore

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.kora.imcore.aidl.ImAidlInterface

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2026/07/24:14:17
 * @Description:
 */
class ImServiceProxy : ServiceConnection {
    private var asInterface: ImAidlInterface? = null

    fun senMessage(msg: String) {
        asInterface?.send(msg)
    }
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        asInterface = ImAidlInterface.Stub.asInterface(service)
        asInterface?.connect("192.168.0.71", 8090)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        asInterface = null
    }
}