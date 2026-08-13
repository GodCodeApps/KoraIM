package com.kora.im

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kora.imcore.IMClient
import com.kora.imcore.ImSdkImpl
import com.kora.imcore.constant.MsgStatus
import com.kora.imcore.db.Message
import com.kora.imcore.db.UserInfo
import com.kora.imcore.provider.IMUserInfoProvider
import com.kora.imui.ImUIKitImpl
import com.kora.imui.listener.sessionEventListener
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var currentAccount: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        configureMessageActions()
        currentAccount = savedInstanceState?.getString(STATE_ACCOUNT)
        if (currentAccount == null) showLogin() else initializeClient(currentAccount!!)
    }

    fun login(account: String) {
        currentAccount = account
        initializeClient(account)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, UserListFragment.newInstance(account))
            .commit()
    }

    private fun initializeClient(account: String) {
        ImSdkImpl.setAccount(account)
        ImUIKitImpl.setAccount(account)
        IMClient.init(applicationContext, SERVER_HOST, SERVER_PORT)
        IMClient.userInfoProvider = object : IMUserInfoProvider {
            override fun getUserInfo(account: String): UserInfo? = DemoUsers.info(account)
            override fun fetchUserInfoFromServer(account: String, callback: (UserInfo?) -> Unit) {
                callback(DemoUsers.info(account))
            }
        }
    }

    fun openChat(account: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, P2PMessageFragment.newInstance(account))
            .addToBackStack(null)
            .commit()
    }

    private fun showLogin() {
        IMClient.release()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    private fun configureMessageActions() {
        ImUIKitImpl.setSessionEventListener(sessionEventListener {
            onAvatarClickListener { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            onResendClickListener { message ->
                (message as? Message)?.let {
                    it.status = MsgStatus.SENDING
                    lifecycleScope.launch { IMClient.sendMessage(it) }
                }
            }
        })
    }

    override fun onDestroy() {
        IMClient.release()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_ACCOUNT, currentAccount)
        super.onSaveInstanceState(outState)
    }

    companion object {
        // Development machine's WLAN address. The phone and computer must be on
        // the same LAN; update this value if the computer's DHCP address changes.
        private const val SERVER_HOST = "192.168.1.5"
        private const val SERVER_PORT = 8090
        private const val STATE_ACCOUNT = "current_account"
    }
}

object DemoUsers {
    val accounts = listOf("test1", "test2", "test3")
    fun info(account: String) = account.takeIf(accounts::contains)?.let {
        UserInfo(it, "用户 ${it.removePrefix("test")}", "")
    }
}
