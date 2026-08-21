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
import com.kora.imui.IMMediaMessageSender
import com.kora.imui.listener.SessionEventListener
import com.kora.imcore.impl.IMMessage
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var currentAccount: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        configureMessageActions()
        currentAccount = savedInstanceState?.getString(STATE_ACCOUNT)
        if (currentAccount == null) showLogin() else {
            initializeClient(currentAccount!!)
            showHome(currentAccount!!)
        }
    }

    fun login(account: String) {
        currentAccount = account
        initializeClient(account)
        showHome(account)
    }

    private fun showHome(account: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment.newInstance(account))
            .commit()
    }

    private fun initializeClient(account: String) {
        ImSdkImpl.setAccount(account)
        IMClient.init(applicationContext, SERVER_HOST, SERVER_PORT)
        ImUIKitImpl.setMediaMessageProvider(AppMediaMessageProvider())
        com.kora.imui.notification.IMNotificationManager.init(this, com.kora.im.chat.ChatActivity::class.java)
        com.kora.imui.notification.IMNotificationManager.requestNotificationPermission(this)
        IMClient.userInfoProvider = object : IMUserInfoProvider {
            override fun getUserInfo(account: String): UserInfo? = DemoUsers.info(account)
            override fun fetchUserInfoFromServer(account: String, callback: (UserInfo?) -> Unit) {
                callback(DemoUsers.info(account))
            }
        }
    }

    fun showLogin() {
        currentAccount = null
        IMClient.release()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    private fun configureMessageActions() {
        ImUIKitImpl.setSessionEventListener(SessionEventListener().apply {
            onAvatarClickListener { account: String? ->
                Toast.makeText(this@MainActivity, account.orEmpty(), Toast.LENGTH_SHORT).show()
            }
            onResendClickListener { message: IMMessage? ->
                (message as? Message)?.let {
                    lifecycleScope.launch {
                        if (IMMediaMessageSender.isMedia(it)) {
                            IMMediaMessageSender.send(it)
                        } else {
                            it.status = MsgStatus.SENDING
                            IMClient.sendMessage(it)
                        }
                    }
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
        private const val SERVER_HOST = "192.168.1.9"
        private const val SERVER_PORT = 8090
        private const val STATE_ACCOUNT = "current_account"
    }
}

/** Demo user data with realistic Chinese names and avatar colors. */
data class DemoUserInfo(
    val account: String,
    val nickname: String,
    val description: String,
    val avatarColor: Int   // color resource id
)

object DemoUsers {
    val accounts = listOf("test1", "test2", "test3", "test4", "test5")

    private val users = listOf(
        DemoUserInfo("test1", "陈晨",   "产品经理 · 北京",    android.R.color.holo_blue_light),
        DemoUserInfo("test2", "林小雨",  "UI 设计师 · 上海",  android.R.color.holo_red_light),
        DemoUserInfo("test3", "王思博",  "后端工程师 · 杭州", android.R.color.holo_orange_light),
        DemoUserInfo("test4", "赵雨桐",  "数据分析师 · 深圳", android.R.color.holo_blue_dark),
        DemoUserInfo("test5", "刘宇飞",  "前端工程师 · 成都", android.R.color.holo_purple)
    )

    private val userMap = users.associateBy { it.account }

    fun demoUser(account: String): DemoUserInfo? = userMap[account]

    fun info(account: String): UserInfo? =
        userMap[account]?.let { UserInfo(it.account, it.nickname, "") }

    /** Avatar background color resource ids in order (index 0..4) */
    val avatarColorRes = listOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3,
        R.color.avatar_4, R.color.avatar_5
    )
}

