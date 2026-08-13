package com.kora.imcore.repository

import com.kora.imcore.db.Message
import com.kora.imcore.db.MessageDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

internal class MessageRepository(private val dao: MessageDao) {
    fun observeSession(sessionId: String): Flow<List<Message>> =
        dao.getMessageBySessionId(sessionId).flowOn(Dispatchers.IO)

    fun observeLastMessage(sessionId: String): Flow<Message> =
        dao.getLaseMessageBySessionId(sessionId).flowOn(Dispatchers.IO)

    suspend fun page(sessionId: String, page: Int): List<Message> = dao.getMessageBySessionId(sessionId, page)
    suspend fun upsert(message: Message) = dao.insertMessage(message)
    suspend fun upsertAll(messages: List<Message>) = dao.insertMessageList(messages)
}
