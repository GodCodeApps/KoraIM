package com.kora.imcore

import android.content.Context
import com.kora.imcore.connection.ConnectionManager
import com.kora.imcore.db.ImAppDatabaseHelper
import com.kora.imcore.db.Message
import com.kora.imcore.db.MessageDao
import com.kora.imcore.db.Conversation
import com.kora.imcore.db.ConversationDao
import com.kora.imcore.db.SyncStateDao
import com.kora.imcore.db.UserDao
import com.kora.imcore.db.UserInfo
import com.kora.imcore.event.ConnectionState
import com.kora.imcore.event.IMEventHub
import com.kora.imcore.impl.IMMessage
import com.kora.imcore.provider.IMUserInfoProvider
import com.kora.imcore.repository.MessageRepository
import com.kora.imcore.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.kora.imcore.listener.UnreadCountListener
import com.kora.imcore.listener.UnreadCountSubscription

/**
 * IM SDK 的公开门面（Facade），是上层使用 KoraIM 的唯一入口。
 *
 * 职责：
 * - 初始化 SDK（数据库、网络连接、消息同步）
 * - 提供消息收发、会话管理、用户信息查询等公开 API
 * - 通过 Kotlin Flow 暴露实时数据流（连接状态、新消息、消息更新、未读数）
 *
 * 使用方式：
 * ```kotlin
 * // 1. 设置当前账号
 * ImSdkImpl.setAccount("user123")
 *
 * // 2. 初始化（内部会创建数据库、绑定后台 Service、建立 TCP 连接）
 * IMClient.init(context, "192.168.1.100", 9090)
 *
 * // 3. 监听连接状态
 * IMClient.connectionState.collect { state -> ... }
 *
 * // 4. 发送消息
 * IMClient.sendMessage(message)
 *
 * // 5. 退出时释放资源
 * IMClient.release()
 * ```
 */
object IMClient {

    /** 收到的新消息流，UI 层可收集此流来刷新聊天列表 */
    val incomingMessages: SharedFlow<Message> get() = IMEventHub.incomingMessages

    /** 消息状态更新流（发送成功/失败等），UI 层可收集此流来更新消息气泡状态 */
    val messageUpdates: SharedFlow<Message> get() = IMEventHub.messageUpdates

    /**
     * 连接状态流，UI 层可收集此流来显示连接指示器。
     * 可能的状态：[ConnectionState.Disconnected]、[ConnectionState.Connecting]、
     * [ConnectionState.Reconnecting]、[ConnectionState.Connected]、[ConnectionState.Failed]
     */
    val connectionState: StateFlow<ConnectionState> get() = IMEventHub.connectionState

    private var connectionManager: ConnectionManager? = null
    private lateinit var messageRepository: MessageRepository
    private lateinit var userRepository: UserRepository
    private lateinit var conversationDao: ConversationDao

    /**
     * 用户信息提供器，上层 App 实现此接口来提供用户头像、昵称等信息。
     * 必须在 [init] 之后设置。
     */
    var userInfoProvider: IMUserInfoProvider?
        get() = if (::userRepository.isInitialized) userRepository.provider else null
        set(value) {
            check(::userRepository.isInitialized) { "Call IMClient.init before setting userInfoProvider" }
            userRepository.provider = value
        }

    /**
     * 初始化 IM SDK。内部流程：
     * 1. 创建 SQLite 数据库（消息、会话、用户、同步游标）
     * 2. 恢复上次的增量同步游标
     * 3. 绑定后台 [IMService]，建立到服务器的 TCP 长连接
     *
     * @param context Android 上下文，内部会自动取 applicationContext
     * @param host 服务器地址
     * @param port 服务器端口（1~65535）
     */
    fun init(context: Context, host: String, port: Int) {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port must be between 1 and 65535" }
        val account = requireNotNull(ImSdkImpl.getAccount()?.takeIf { it.isNotBlank() }) {
            "Call ImSdkImpl.setAccount(account) before IMClient.init"
        }
        release()
        val appContext = context.applicationContext
        val database = ImAppDatabaseHelper(appContext)
        val syncCursor = SyncStateDao(database).getCursor(account)
        conversationDao = ConversationDao(database)
        messageRepository = MessageRepository(MessageDao(database, conversationDao))
        userRepository = UserRepository(UserDao(database), IMRuntime.scope)
        IMRuntime.messages = messageRepository
        IMRuntime.ownerId = account
        IMRuntime.syncCursor = syncCursor
        ImSdkImpl.init()
        connectionManager = ConnectionManager(appContext).also { it.connect(host, port, account, syncCursor) }
    }

    /**
     * 发送消息。内部会先持久化到本地数据库并更新会话列表（状态为 SENDING），
     * 然后通过 TCP 连接发送到服务器，等待 ACK 后更新状态为 SUCCESS 或 FAIL。
     */
    suspend fun sendMessage(message: IMMessage) {
        ensureInitialized()
        val msg = message.getMessage()
        if (msg.sessionId.isNotBlank()) {
            messageRepository.confirm(msg, IMRuntime.ownerId)
        } else {
            messageRepository.upsert(msg)
        }
        connectionManager?.send(message)
    }

    /** 按会话 ID 观察消息列表（实时更新） */
    fun observeMessages(sessionId: String): Flow<List<Message>> {
        ensureInitialized()
        return messageRepository.observeSession(sessionId)
    }

    /** 按对方用户 ID 观察 P2P 消息列表（实时更新） */
    fun observeP2PMessages(peerId: String): Flow<List<Message>> {
        ensureInitialized()
        return messageRepository.observeP2P(IMRuntime.ownerId, peerId)
    }

    /** 获取与指定用户的 P2P 会话信息 */
    suspend fun getP2PConversation(peerId: String): Conversation? {
        ensureInitialized()
        return conversationDao.findP2P(IMRuntime.ownerId, peerId)
    }

    /** 获取当前账号的所有会话列表 */
    suspend fun getConversations(): List<Conversation> {
        ensureInitialized()
        return conversationDao.getAll(IMRuntime.ownerId)
    }

    /** 观察当前账号的会话列表（实时更新） */
    fun observeConversations(): Flow<List<Conversation>> {
        ensureInitialized()
        return conversationDao.observeAll(IMRuntime.ownerId)
    }

    /** 观察当前账号所有会话的总未读数（实时更新，去重发射） */
    fun observeTotalUnreadCount(): Flow<Int> {
        ensureInitialized()
        return conversationDao.observeAll(IMRuntime.ownerId)
            .map { conversations ->
                conversations.fold(0L) { total, item -> total + item.unreadCount }
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            }
            .distinctUntilChanged()
    }

    /**
     * Java 友好的未读数监听器。
     * 返回 [UnreadCountSubscription]，不再需要时调用 cancel() 取消订阅。
     */
    fun addUnreadCountListener(listener: UnreadCountListener): UnreadCountSubscription {
        ensureInitialized()
        val job = IMRuntime.scope.launch {
            observeTotalUnreadCount().collect { count ->
                withContext(Dispatchers.Main.immediate) {
                    listener.onUnreadCountChanged(count)
                }
            }
        }
        return UnreadCountSubscription { job.cancel() }
    }

    /** 将指定会话标记为已读，清除未读计数 */
    suspend fun markConversationRead(sessionId: String) {
        ensureInitialized()
        if (sessionId.isNotBlank()) conversationDao.markRead(IMRuntime.ownerId, sessionId)
    }

    /** 观察指定会话的最新一条消息（用于会话列表的消息预览） */
    fun observeLastMessage(sessionId: String): Flow<Message> {
        ensureInitialized()
        return messageRepository.observeLastMessage(sessionId)
    }

    /** 根据消息 ID 查询单条消息 */
    fun getMessage(messageId: String): Message? {
        ensureInitialized()
        return messageRepository.getMessageById(messageId)
    }

    /** 分页查询指定会话的消息列表 */
    suspend fun getMessagePage(sessionId: String, page: Int): List<Message> {
        ensureInitialized()
        return messageRepository.page(sessionId, page)
    }

    /** 保存单条消息到本地数据库（不通过网络发送） */
    suspend fun saveMessage(message: IMMessage) {
        ensureInitialized()
        messageRepository.upsert(message.getMessage())
    }

    /** 从本地数据库删除单条消息，并自动同步更新会话最新预览 */
    suspend fun deleteMessage(messageId: String) {
        ensureInitialized()
        if (messageId.isNotBlank()) {
            messageRepository.delete(messageId, IMRuntime.ownerId)
        }
    }

    /** 从本地数据库删除整个会话及其下的所有消息记录 */
    suspend fun deleteConversation(sessionId: String) {
        ensureInitialized()
        if (sessionId.isNotBlank()) {
            conversationDao.delete(IMRuntime.ownerId, sessionId)
        }
    }

    /** 根据消息 ID 查询单条消息 */
    suspend fun getMessageById(messageId: String): Message? = withContext(Dispatchers.IO) {
        ensureInitialized()
        messageRepository.getMessageById(messageId)
    }

    /** 批量保存消息到本地数据库 */
    suspend fun saveMessages(messages: List<Message>) {
        ensureInitialized()
        messageRepository.upsertAll(messages)
    }

    /** 查询用户信息（先查缓存 → 数据库 → Provider 远程拉取） */
    suspend fun getUserInfo(account: String?): UserInfo? {
        ensureInitialized()
        return userRepository.get(account)
    }

    /**
     * 释放 SDK 资源：断开连接、清除缓存、取消后台协程。
     * 切换账号前必须调用此方法。
     */
    fun release() {
        connectionManager?.disconnect()
        connectionManager = null
        if (::userRepository.isInitialized) userRepository.clear()
        IMRuntime.reset()
    }

    /** 检查 SDK 是否已初始化 */
    private fun ensureInitialized() {
        check(connectionManager != null && ::messageRepository.isInitialized) {
            "IMClient.init(context, host, port) must be called first"
        }
    }
}
