package com.kora.im

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kora.imcore.IMClient
import com.kora.imcore.ImSdkImpl
import com.kora.imui.ImUIKitImpl
import com.kora.imui.listener.sessionEventListener


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, P2PMessageFragment()).commit()
        ImSdkImpl.init()
        ImSdkImpl.setAccount("test2")
        // Initialize IM client with local node.js test server config
        IMClient.init(this, "192.168.1.5", 8090)
        ImUIKitImpl.setSessionEventListener(sessionEventListener {
            onAvatarClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "onAvatarClickListener>${it}",
                    Toast.LENGTH_LONG
                ).show()
            }
            onAvatarLongClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "onAvatarLongClickListener>${it}",
                    Toast.LENGTH_LONG
                ).show()
            }
            onItemClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "onItemClickListener>${it?.getAttachment()}",
                    Toast.LENGTH_LONG
                ).show()
            }
            onResendClickListener { msg ->
                if (msg != null && msg is com.kora.imcore.db.Message) {
                    msg.status = com.zchd.vsports.im.core.constant.MsgStatus.SENDING
                    IMClient.updateMessageToLocal(msg)
                    IMClient.getMessageChangeListener()?.invoke(msg)
                    IMClient.sendMessage(msg)
                }
            }
        })

    }
}