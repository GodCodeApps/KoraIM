package com.kora.imcore

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.kora.imcore.event.ConnectionState
import com.kora.imcore.event.IMEventHub
import java.util.concurrent.ConcurrentLinkedQueue

/** Same-process Service connection. IMService is intentionally not exported or remote. */
internal class ImServiceProxy : ServiceConnection {
    private var service: IMService? = null
    private var host = ""
    private var port = 0
    private var account = ""
    private var syncCursor = 0L
    private val pendingMessages = ConcurrentLinkedQueue<String>()

    fun setServerConfig(host: String, port: Int, account: String, syncCursor: Long) {
        this.host = host
        this.port = port
        this.account = account
        this.syncCursor = syncCursor
    }

    fun sendMessage(message: String) {
        service?.send(message) ?: pendingMessages.offer(message)
    }

    fun disconnect() {
        service?.disconnect()
        service = null
        pendingMessages.clear()
        IMEventHub.setConnectionState(ConnectionState.Disconnected)
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = (binder as? IMService.LocalBinder)?.service
        val connectedService = service ?: return
        connectedService.connect(host, port, account, syncCursor)
        while (true) connectedService.send(pendingMessages.poll() ?: break)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.w(TAG, "IM service disconnected")
        service = null
        IMEventHub.setConnectionState(ConnectionState.Disconnected)
    }

    private companion object { const val TAG = "KoraIM" }
}
