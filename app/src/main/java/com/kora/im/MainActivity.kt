package com.kora.im

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kora.imcore.IMClient
import com.kora.imcore.ImSdkImpl
import com.kora.imcore.constant.MsgStatus
import com.kora.imcore.db.Message
import com.kora.imcore.db.UserInfo
import com.kora.imcore.impl.IMMessage
import com.kora.imcore.provider.IMUserInfoProvider
import com.kora.imui.IMMediaMessageSender
import com.kora.imui.ImUIKitImpl
import com.kora.imui.listener.SessionEventListener
import com.kora.imui.notification.IMNotificationManager
import com.kora.imcall.IMCall
import com.kora.onsim.asr.OnnxSimAsr
import com.kora.onsim.asr.OnnxSimAsrInitializationListener
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var currentAccount: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeAsrEngine()
        configureMessageActions()
        observeKickEvents()
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
        IMClient.init(
            applicationContext,
            SERVER_HOST,
            SERVER_PORT,
            SERVER_TLS_ENABLED,
            SERVER_WIRE_LOG_ENABLED
        )
        IMCall.init(applicationContext)
        ImUIKitImpl.setMediaMessageProvider(AppMediaMessageProvider())
        IMNotificationManager.init(this, com.kora.im.chat.ChatActivity::class.java)
        IMNotificationManager.requestNotificationPermission(this)
        IMClient.userInfoProvider = object : IMUserInfoProvider {
            override fun getUserInfo(account: String): UserInfo? = null
            override fun fetchUserInfoFromServer(account: String, callback: (UserInfo?) -> Unit) {
                callback(DemoUsers.info(account))
            }
        }
    }

    private fun observeKickEvents() {
        lifecycleScope.launch {
            IMClient.kickEvents.collect { reason ->
                Toast.makeText(applicationContext, reason.ifBlank { "您的账号已在其他设备登录" }, Toast.LENGTH_LONG).show()
                // 若用户当前在子页面（例如 ChatActivity、CallActivity），将其清掉并把 MainActivity 拉到前台
                val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                showLogin()
            }
        }
    }

    fun showLogin() {
        currentAccount = null
        IMClient.release()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commitAllowingStateLoss()
    }

    private fun configureMessageActions() {
        ImUIKitImpl.setSessionEventListener(SessionEventListener().apply {
            onAvatarClickListener { view, account: String? ->
                Toast.makeText(this@MainActivity, account.orEmpty(), Toast.LENGTH_SHORT).show()
            }
            onResendClickListener { view, message: IMMessage? ->
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
        OnnxSimAsr.release()
        IMClient.release()
        super.onDestroy()
    }

    private fun initializeAsrEngine() {
        OnnxSimAsr.initialize(applicationContext, object : OnnxSimAsrInitializationListener {
            override fun onInitialized() {
                android.util.Log.i("KoraIM_ASR", "ASR engine initialized")
            }

            override fun onError(error: Throwable) {
                android.util.Log.e("KoraIM_ASR", "ASR engine initialization failed", error)
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_ACCOUNT, currentAccount)
        super.onSaveInstanceState(outState)
    }

    companion object {
        // Development machine's WLAN address. The phone and computer must be on
        // the same LAN; update this value if the computer's DHCP address changes.
        private const val SERVER_HOST = "192.168.31.164"
        private const val SERVER_PORT = 8090
        private const val SERVER_TLS_ENABLED = true
        private const val SERVER_WIRE_LOG_ENABLED = true
        private const val STATE_ACCOUNT = "current_account"
    }
}

/** Demo user data with realistic Chinese names and avatar colors. */
data class DemoUserInfo(
    val account: String,
    val nickname: String,
    val description: String,
    val avatarColor: Int,   // color resource id
    val avatarUrl: String
)

object DemoUsers {
    val accounts = listOf("test1", "test2", "test3", "test4", "test5")

    private val users = listOf(
        DemoUserInfo(
            "test1",
            "陈晨",
            "产品经理 · 北京",
            android.R.color.holo_blue_light,
            "https://img0.baidu.com/it/u=4090671433,2993438630&fm=253&fmt=auto?w=830&h=800"
        ),
        DemoUserInfo(
            "test2",
            "林小雨",
            "UI 设计师 · 上海",
            android.R.color.holo_red_light,
            "https://img0.baidu.com/it/u=2678472382,3134615811&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=500"
        ),
        DemoUserInfo(
            "test3",
            "王思博",
            "后端工程师 · 杭州",
            android.R.color.holo_orange_light,
            "https://img0.baidu.com/it/u=2894886765,3228418388&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=500"
        ),
        DemoUserInfo(
            "test4",
            "赵雨桐",
            "数据分析师 · 深圳",
            android.R.color.holo_blue_dark,
            "https://img2.baidu.com/it/u=2398229740,959087329&fm=253&fmt=auto&app=120&f=JPEG?w=800&h=800"
        ),
        DemoUserInfo(
            "test5",
            "刘宇飞",
            "前端工程师 · 成都",
            android.R.color.holo_purple,
            "https://img1.baidu.com/it/u=1945685654,1523484003&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=500"
        )
    )

    private val userMap = users.associateBy { it.account }

    fun demoUser(account: String): DemoUserInfo? = userMap[account]

    fun info(account: String): UserInfo? =
        userMap[account]?.let { UserInfo(it.account, it.nickname, it.avatarUrl) }

    /** Avatar background color resource ids in order (index 0..4) */
    val avatarColorRes = listOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3,
        R.color.avatar_4, R.color.avatar_5
    )
}

