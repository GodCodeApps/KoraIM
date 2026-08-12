package com.kora.imcore

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.google.gson.Gson
import com.kora.imcore.db.ImAppDatabase
import com.kora.imcore.db.Message
import com.kora.imcore.db.MessageDao
import com.kora.imcore.impl.IMMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/12/22:11:40
 * @Description:
 */
object IMClient {
    private var serviceProxy = ImServiceProxy()

    private var onReceiveListener: ((Message) -> Unit)? = null
    private var onMessageChangeListener: ((Message) -> Unit)? = null
    private var receiveListeners = mutableMapOf<String, ((Message) -> Unit)>()
    private lateinit var mContext: Context
    private lateinit var messageDao: MessageDao

    fun sendMessage(msg: IMMessage) {
        serviceProxy.senMessage(Gson().toJson(msg))
    }

    fun registerReceiveListener(context: Context, listener: ((Message) -> Unit)) {
        receiveListeners.put(context.javaClass.canonicalName, listener)
    }

    fun unRegisterReceiveListener(context: Context) {
        receiveListeners.remove(context.javaClass.canonicalName)
    }

    fun getReceiveListener(): List<((Message) -> Unit)?> {
        return receiveListeners.values.toList()
    }

    fun registerMessageChangeListener(listener: ((Message) -> Unit)?) {
        onMessageChangeListener = listener
    }

    fun unRegisterMessageChangeListener() {
        onMessageChangeListener = null
    }

    fun getMessageChangeListener(): ((Message) -> Unit)? {
        return onMessageChangeListener
    }

    fun init(context: Context) {
        mContext = context
        ImSdkImpl.init()
        messageDao = ImAppDatabase.getInstance(mContext).messageDao()
        context.bindService(
            Intent(context, IMService::class.java),
            serviceProxy,
            Context.BIND_AUTO_CREATE
        )
    }

    fun queryLaseMessageBySessionId(
        sessionId: String
    ): LiveData<Message>? {
        return messageDao.getLaseMessageBySessionId(sessionId).flowOn(Dispatchers.IO)
            ?.asLiveData()

    }

    fun queryAllMessageListBySessionId(
        sessionId: String
    ): LiveData<List<Message>>? {
        return messageDao.getMessageBySessionId(sessionId)?.flowOn(Dispatchers.IO)?.asLiveData()
    }

    fun queryMessageListByPageSize(
        sessionId: String,
        page: Int
    ): LiveData<List<Message>> {
        var liveData = MutableLiveData<List<Message>>()
        GlobalScope.launch(Dispatchers.Main) {
            val list = messageDao.getMessageBySessionId(sessionId, page)
            liveData.value = list
        }
        return liveData
    }

    fun saveMessageToLocal(msg: IMMessage) {
        GlobalScope.launch(Dispatchers.IO) {
            messageDao.insertMessage(msg.getMessage())
        }
    }

    fun saveMessageListToLocal(msg: List<Message>) {
        GlobalScope.launch {
            messageDao.insertMessageList(msg)
        }
    }

    fun updateMessageToLocal(message: Message) {
        GlobalScope.launch {
            if (null != messageDao.getMessageByMessageId(message.getMsgId())) {
                messageDao.updateMessage(message.getMsgId())
            } else {
                messageDao.insertMessage(message)
            }
        }
    }

    fun insertMessage(message: Message) {
        GlobalScope.launch {
            messageDao.insertMessage(message)
        }
    }

    fun clear() {


    }
}