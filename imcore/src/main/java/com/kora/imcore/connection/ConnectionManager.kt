package com.kora.imcore.connection

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.kora.imcore.IMService
import com.kora.imcore.ImServiceProxy
import com.kora.imcore.impl.IMMessage

internal class ConnectionManager(private val context: Context) {
    private val gson = Gson()
    private val proxy = ImServiceProxy()
    private var bound = false

    fun connect(host: String, port: Int) {
        disconnect()
        proxy.setServerConfig(host, port)
        bound = context.bindService(Intent(context, IMService::class.java), proxy, Context.BIND_AUTO_CREATE)
        check(bound) { "Unable to bind KoraIM service" }
    }

    fun send(message: IMMessage) {
        check(bound) { "IMClient.init(context, host, port) must be called before sendMessage" }
        proxy.sendMessage(gson.toJson(message))
    }

    fun disconnect() {
        proxy.disconnect()
        if (bound) runCatching { context.unbindService(proxy) }
        bound = false
    }
}
