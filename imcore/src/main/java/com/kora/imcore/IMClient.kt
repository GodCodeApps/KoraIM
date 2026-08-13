package com.kora.imcore

import android.content.Context
import com.kora.imcore.connection.ConnectionManager
import com.kora.imcore.db.ImAppDatabaseHelper
import com.kora.imcore.db.Message
import com.kora.imcore.db.MessageDao
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

/** Public SDK facade. Transport, persistence and caches live in dedicated components. */
object IMClient {
    val incomingMessages: SharedFlow<Message> get() = IMEventHub.incomingMessages
    val messageUpdates: SharedFlow<Message> get() = IMEventHub.messageUpdates
    val connectionState: StateFlow<ConnectionState> get() = IMEventHub.connectionState

    private var connectionManager: ConnectionManager? = null
    private lateinit var messageRepository: MessageRepository
    private lateinit var userRepository: UserRepository

    var userInfoProvider: IMUserInfoProvider?
        get() = if (::userRepository.isInitialized) userRepository.provider else null
        set(value) {
            check(::userRepository.isInitialized) { "Call IMClient.init before setting userInfoProvider" }
            userRepository.provider = value
        }

    fun init(context: Context, host: String, port: Int) {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port must be between 1 and 65535" }
        val account = requireNotNull(ImSdkImpl.getAccount()?.takeIf { it.isNotBlank() }) {
            "Call ImSdkImpl.setAccount(account) before IMClient.init"
        }
        release()
        val appContext = context.applicationContext
        val database = ImAppDatabaseHelper(appContext)
        messageRepository = MessageRepository(MessageDao(database))
        userRepository = UserRepository(UserDao(database), IMRuntime.scope)
        IMRuntime.messages = messageRepository
        ImSdkImpl.init()
        connectionManager = ConnectionManager(appContext).also { it.connect(host, port, account) }
    }

    suspend fun sendMessage(message: IMMessage) {
        ensureInitialized()
        messageRepository.upsert(message.getMessage())
        connectionManager?.send(message)
    }

    fun observeMessages(sessionId: String): Flow<List<Message>> {
        ensureInitialized()
        return messageRepository.observeSession(sessionId)
    }

    fun observeLastMessage(sessionId: String): Flow<Message> {
        ensureInitialized()
        return messageRepository.observeLastMessage(sessionId)
    }

    suspend fun getMessagePage(sessionId: String, page: Int): List<Message> {
        ensureInitialized()
        return messageRepository.page(sessionId, page)
    }

    suspend fun saveMessage(message: IMMessage) {
        ensureInitialized()
        messageRepository.upsert(message.getMessage())
    }

    suspend fun saveMessages(messages: List<Message>) {
        ensureInitialized()
        messageRepository.upsertAll(messages)
    }

    suspend fun getUserInfo(account: String?): UserInfo? {
        ensureInitialized()
        return userRepository.get(account)
    }

    fun release() {
        connectionManager?.disconnect()
        connectionManager = null
        if (::userRepository.isInitialized) userRepository.clear()
    }

    private fun ensureInitialized() {
        check(connectionManager != null && ::messageRepository.isInitialized) {
            "IMClient.init(context, host, port) must be called first"
        }
    }
}
