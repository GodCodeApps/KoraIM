// Copyright 2026 GodCodeApps. Licensed under the Apache License, Version 2.0.
package com.kora.imui.notification

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kora.imcore.IMClient
import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.db.Message
import com.kora.imui.R
import com.kora.imui.provider.ConversationDigestFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bumptech.glide.Glide

/**
 * IM 消息通知管理器：
 * 1. 适配 Android 7.0 ~ Android 14+ 消息通知栏弹窗（含系统提示音、顶部悬浮横幅 Heads-up、震动、呼吸灯）。
 * 2. 升级全新渠道 ID 避免系统渠道缓存导致静音或横幅不弹出的问题。
 * 3. 智能免打扰：当前正在聊天的前台会话自动忽略通知。
 * 4. 支持点击通知直接拉起对应聊天室。
 */
object IMNotificationManager {
    // 升级 Channel ID 到 v2，冲刷掉老版本系统缓存中的不可变渠道配置
    private const val CHANNEL_ID = "kora_im_chat_messages_v2"
    private const val CHANNEL_NAME = "即时聊天消息"
    private const val CHANNEL_DESC = "用于接收单聊与群聊新消息，支持悬浮横幅与声音提示"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isInitialized = false
    private var observeJob: Job? = null
    private var appContext: Context? = null
    private var targetChatActivityClass: Class<out Activity>? = null

    /** 震动波形：静止 0ms，震动 250ms，间隔 200ms，再震动 250ms */
    private val VIBRATE_PATTERN = longArrayOf(0, 250, 200, 250)

    /** 当前用户正在浏览的前台会话 ID 和对方账号（用于前台免打扰） */
    @Volatile
    var currentActiveSessionId: String? = null
        private set

    @Volatile
    var currentActivePeerId: String? = null
        private set

    /**
     * 初始化消息通知管理器
     *
     * @param context 应用程序上下文
     * @param chatActivityClass 点击通知栏跳转的目标聊天 Activity（例如 ChatActivity.class）
     */
    @JvmStatic
    @JvmOverloads
    fun init(context: Context, chatActivityClass: Class<out Activity>? = null) {
        val app = context.applicationContext
        this.appContext = app
        this.targetChatActivityClass = chatActivityClass

        createNotificationChannel(app)
        startObservingIncomingMessages()
        isInitialized = true
    }

    /**
     * 设置当前处于前台焦点的会话（进入聊天页面时调用）
     */
    @JvmStatic
    fun setCurrentActiveSession(sessionId: String?, peerId: String? = null) {
        this.currentActiveSessionId = sessionId
        this.currentActivePeerId = peerId
        if (!sessionId.isNullOrEmpty()) {
            cancelNotification(sessionId)
        }
    }

    /**
     * 清除指定会话的通知
     */
    @JvmStatic
    fun cancelNotification(sessionId: String) {
        val context = appContext ?: return
        if (sessionId.isNotEmpty()) {
            NotificationManagerCompat.from(context).cancel(sessionId.hashCode())
        }
    }

    /**
     * 清除所有 IM 相关的通知
     */
    @JvmStatic
    fun cancelAllNotifications() {
        val context = appContext ?: return
        NotificationManagerCompat.from(context).cancelAll()
    }

    /**
     * 检查是否具备通知权限
     */
    @JvmStatic
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 在 Android 13+ 上请求通知权限辅助方法
     */
    @JvmStatic
    fun requestNotificationPermission(activity: Activity, requestCode: Int = 1001) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = "android.permission.POST_NOTIFICATIONS"
            if (ContextCompat.checkSelfPermission(activity, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
            }
        }
    }

    /**
     * 跳转至系统通知渠道设置页（方便用户手动开启国内厂商 ROM 的悬浮通知/横幅权限）
     */
    @JvmStatic
    fun openNotificationChannelSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 删除旧版可能已被系统锁定为静音/低优先级的旧渠道
            runCatching {
                notificationManager.deleteNotificationChannel("kora_im_chat_messages")
            }

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH // 必须为 HIGH 才能触发悬浮横幅 (Heads-up)
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                vibrationPattern = VIBRATE_PATTERN
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startObservingIncomingMessages() {
        observeJob?.cancel()
        observeJob = scope.launch(Dispatchers.IO) {
            IMClient.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    private suspend fun handleIncomingMessage(message: Message) {
        val context = appContext ?: return

        // 1. 过滤自己发出的消息
        if (message.direct == MsgDirection.OUT) return

        // 2. 智能免打扰：当前正停留在该聊天页面
        val activeSession = currentActiveSessionId
        val activePeer = currentActivePeerId
        val isBelongsToCurrentChat = (!activeSession.isNullOrEmpty() && message.sessionId == activeSession) ||
                (!activePeer.isNullOrEmpty() && (message.senderId == activePeer || message.receiverId == activePeer))

        if (isBelongsToCurrentChat) return

        // 3. 解析发信人昵称
        val userInfo = IMClient.getUserInfo(message.senderId)
        val title = userInfo?.nickname?.takeIf { it.isNotBlank() } ?: message.senderId

        // 4. 解析消息摘要（富媒体、红包、提示等自动适配）
        val content = ConversationDigestFormatter.formatMessage(message)

        // 5. 构建并弹出通知
        showNotification(context, message, title, content, userInfo?.avatar)
    }

    private suspend fun showNotification(
        context: Context,
        message: Message,
        title: String,
        content: String,
        avatarUrl: String?
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        // Notification large icons must be actual Bitmaps. Download off the main thread;
        // missing/invalid avatars fall back to the app logo.
        val largeIcon = withContext(Dispatchers.IO) {
            val remote = avatarUrl?.takeIf { it.isNotBlank() }?.let {
                runCatching { Glide.with(context).asBitmap().load(it).submit(128, 128).get() }.getOrNull()
            }
            remote ?: BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground)
        }

        // 构造点击跳转 Intent
        val clickIntent = if (targetChatActivityClass != null) {
            Intent(context, targetChatActivityClass).apply {
                putExtra("session_type", message.sessionType)
                putExtra("session_id", message.sessionId)
                putExtra("peer_id", message.senderId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } ?: Intent()
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val notificationId = if (message.sessionId.isNotEmpty()) message.sessionId.hashCode() else message.senderId.hashCode()

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            clickIntent,
            pendingIntentFlags
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_default_avatar)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_MAX) // 最高优先级，促使横幅浮窗弹出
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSound(soundUri)
            .setVibrate(VIBRATE_PATTERN)
            .setLights(Color.GREEN, 500, 1000)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
