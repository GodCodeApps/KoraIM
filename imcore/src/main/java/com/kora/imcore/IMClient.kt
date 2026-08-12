@file:Suppress("unused", "MemberVisibilityCanBePrivate", "SpellCheckingInspection")

package com.kora.imcore

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.google.gson.Gson
import com.kora.imcore.db.ImAppDatabaseHelper
import com.kora.imcore.db.Message
import com.kora.imcore.db.MessageDao
import com.kora.imcore.db.UserDao
import com.kora.imcore.db.UserInfo
import com.kora.imcore.impl.IMMessage
import com.kora.imcore.provider.IMUserInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.liveData

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2026/07/22:11:40
 * @Description:
 */
object IMClient {
    private var serviceProxy = ImServiceProxy()

    private var onReceiveListener: ((Message) -> Unit)? = null
    private var onMessageChangeListener: ((Message) -> Unit)? = null
    private var receiveListeners = mutableMapOf<String, ((Message) -> Unit)>()
    
    private lateinit var messageDao: MessageDao
    private lateinit var userDao: UserDao
    
    var userInfoProvider: IMUserInfoProvider? = null
    
    private val userInfoCache = mutableMapOf<String, UserInfo>()
    
    private val imScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 发送消息到底层 Service
     */
    fun sendMessage(msg: IMMessage) {
        serviceProxy.senMessage(Gson().toJson(msg))
    }

    /**
     * 注册消息接收监听器
     */
    fun registerReceiveListener(context: Context, listener: ((Message) -> Unit)) {
        receiveListeners[context.javaClass.name] = listener
    }

    /**
     * 注销消息接收监听器
     */
    fun unRegisterReceiveListener(context: Context) {
        receiveListeners.remove(context.javaClass.name)
    }

    /**
     * 获取所有已注册的消息接收监听器
     */
    fun getReceiveListener(): List<((Message) -> Unit)?> {
        return receiveListeners.values.toList()
    }

    /**
     * 注册消息变更监听器 (如状态更新)
     */
    fun registerMessageChangeListener(listener: ((Message) -> Unit)?) {
        onMessageChangeListener = listener
    }

    /**
     * 注销消息变更监听器
     */
    fun unRegisterMessageChangeListener() {
        onMessageChangeListener = null
    }

    /**
     * 获取当前的消息变更监听器
     */
    fun getMessageChangeListener(): ((Message) -> Unit)? {
        return onMessageChangeListener
    }

    /**
     * 初始化 IM 客户端，绑定底层服务并建立连接
     */
    fun init(context: Context, host: String, port: Int) {
        val appContext = context.applicationContext
        ImSdkImpl.init()
        val dbHelper = ImAppDatabaseHelper(appContext)
        messageDao = MessageDao(dbHelper)
        userDao = UserDao(dbHelper)
        
        serviceProxy.setServerConfig(host, port)
        
        appContext.bindService(
            Intent(appContext, IMService::class.java),
            serviceProxy,
            Context.BIND_AUTO_CREATE
        )
    }

    /**
     * 查询指定会话的最后一条消息
     */
    fun queryLaseMessageBySessionId(
        sessionId: String
    ): LiveData<Message>? {
        return messageDao.getLaseMessageBySessionId(sessionId).flowOn(Dispatchers.IO)
            ?.asLiveData()

    }

    /**
     * 查询指定会话的所有消息
     */
    fun queryAllMessageListBySessionId(
        sessionId: String
    ): LiveData<List<Message>>? {
        return messageDao.getMessageBySessionId(sessionId)?.flowOn(Dispatchers.IO)?.asLiveData()
    }

    /**
     * 分页查询指定会话的消息列表
     */
    fun queryMessageListByPageSize(
        sessionId: String,
        page: Int
    ): LiveData<List<Message>> = liveData(Dispatchers.IO) {
        emit(messageDao.getMessageBySessionId(sessionId, page))
    }

    /**
     * 保存单条 IMMessage 到本地数据库
     */
    fun saveMessageToLocal(msg: IMMessage) {
        imScope.launch {
            messageDao.insertMessage(msg.getMessage())
        }
    }

    /**
     * 批量保存消息列表到本地数据库
     */
    fun saveMessageListToLocal(msg: List<Message>) {
        imScope.launch {
            messageDao.insertMessageList(msg)
        }
    }

    /**
     * 更新或插入一条消息到本地数据库
     */
    fun updateMessageToLocal(message: Message) {
        imScope.launch {
            if (null != messageDao.getMessageByMessageId(message.getMsgId())) {
                messageDao.updateMessage(message.getMsgId(), message.getMsgStatus())
            } else {
                messageDao.insertMessage(message)
            }
        }
    }

    /**
     * 插入单条消息到本地数据库
     */
    fun insertMessage(message: Message) {
        imScope.launch {
            messageDao.insertMessage(message)
        }
    }

    /**
     * 清理资源 (暂未实现)
     */
    fun clear() {

    }
    
    /**
     * 获取用户信息，支持三级缓存: 内存 -> 数据库 -> App层接口/服务器
     */
    fun getUserInfo(account: String?, callback: (UserInfo?) -> Unit) {
        if (account.isNullOrEmpty()) {
            callback(null)
            return
        }
        
        // 1. Check memory cache
        val cachedInfo = userInfoCache[account]
        if (cachedInfo != null) {
            callback(cachedInfo)
            return
        }
        
        imScope.launch {
            // 2. Check local database
            var dbInfo = userDao.getUserInfo(account)
            
            // 3. Fallback to App layer provider if configured
            if (dbInfo == null && userInfoProvider != null) {
                // If it's a synchronous provider method
                dbInfo = userInfoProvider?.getUserInfo(account)
                if (dbInfo != null) {
                    userDao.insertOrUpdateUserInfo(dbInfo)
                }
            }
            
            if (dbInfo != null) {
                userInfoCache[account] = dbInfo
                withContext(Dispatchers.Main) {
                    callback(dbInfo)
                }
            } else {
                // 4. If still null, trigger async fetch from server
                userInfoProvider?.fetchUserInfoFromServer(account) { fetchedInfo ->
                    if (fetchedInfo != null) {
                        userInfoCache[account] = fetchedInfo
                        imScope.launch {
                            userDao.insertOrUpdateUserInfo(fetchedInfo)
                        }
                    }
                    callback(fetchedInfo)
                } ?: run {
                    withContext(Dispatchers.Main) {
                        callback(null)
                    }
                }
            }
        }
    }
}