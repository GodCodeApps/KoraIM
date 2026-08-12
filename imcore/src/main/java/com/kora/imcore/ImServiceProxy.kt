package com.kora.imcore

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.kora.imcore.aidl.ImAidlInterface
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2026/07/24:14:17
 * @Description:
 */
class ImServiceProxy : ServiceConnection {
    private var asInterface: ImAidlInterface? = null
    
    private var host: String = ""
    private var port: Int = 0
    private var isConnected = false
    
    // Thread-safe queue for pending messages that are sent before the service is connected
    private val pendingMessages = ConcurrentLinkedQueue<String>()

    fun setServerConfig(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    fun senMessage(msg: String) {
        if (isConnected && asInterface != null) {
            asInterface?.send(msg)
        } else {
            Log.w("ImServiceProxy", "Service not connected yet. Message queued.")
            pendingMessages.offer(msg)
        }
    }
    
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.i("ImServiceProxy", "onServiceConnected: bind success")
        asInterface = ImAidlInterface.Stub.asInterface(service)
        isConnected = true
        
        // Connect to the configured server
        if (host.isNotEmpty() && port > 0) {
            asInterface?.connect(host, port)
        } else {
            Log.e("ImServiceProxy", "Server configuration is missing! Cannot connect.")
        }
        
        // Flush pending messages
        while (!pendingMessages.isEmpty()) {
            val msg = pendingMessages.poll()
            if (msg != null) {
                asInterface?.send(msg)
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.w("ImServiceProxy", "onServiceDisconnected: connection lost")
        asInterface = null
        isConnected = false
    }
}