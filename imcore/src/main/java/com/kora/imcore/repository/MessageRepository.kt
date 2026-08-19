package com.kora.imcore.repository

import com.kora.imcore.db.Message
import com.kora.imcore.db.MessageDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import com.kora.imcore.netty.SyncEvent

/**
 * 消息数据仓库，封装 [MessageDao] 的操作并统一在 IO 线程执行。
 *
 * 提供两类接口：
 * - **观察（observe）**：返回 Flow，数据变化时自动重新发射（用于 UI 实时刷新）
 * - **操作（upsert/page/confirm）**：挂起函数，执行一次性读写
 */
internal class MessageRepository(private val dao: MessageDao) {

    /** 按会话 ID 观察消息列表（实时更新） */
    fun observeSession(sessionId: String): Flow<List<Message>> =
        dao.getMessageBySessionId(sessionId).flowOn(Dispatchers.IO)

    /** 按双方 ID 观察 P2P 消息列表（实时更新） */
    fun observeP2P(ownerId: String, peerId: String): Flow<List<Message>> =
        dao.getP2PMessages(ownerId, peerId).flowOn(Dispatchers.IO)

    /** 观察指定会话的最新一条消息（用于会话列表预览） */
    fun observeLastMessage(sessionId: String): Flow<Message> =
        dao.getLaseMessageBySessionId(sessionId).flowOn(Dispatchers.IO)

    /** 分页查询消息列表 */
    suspend fun page(sessionId: String, page: Int): List<Message> = dao.getMessageBySessionId(sessionId, page)

    /** 插入或更新单条消息 */
    suspend fun upsert(message: Message) = dao.insertMessage(message)

    /** 批量插入或更新消息 */
    suspend fun upsertAll(messages: List<Message>) = dao.insertMessageList(messages)

    /**
     * 确认消息（发送成功/收到新消息时调用）。
     * 会更新消息状态、关联会话、更新会话的最后消息预览和未读计数。
     */
    suspend fun confirm(message: Message, ownerId: String) = dao.confirmMessage(message, ownerId)

    /**
     * 应用增量同步事件。
     * 批量处理离线期间的消息变更，并更新同步游标。
     */
    suspend fun applySync(ownerId: String, events: List<SyncEvent>, cursor: Long) =
        dao.applySync(ownerId, events, cursor)

    /** 根据消息 ID 查询单条消息（同步方法） */
    fun getMessageById(messageId: String): Message? = dao.getMessageByMessageId(messageId)

    /** 删除单条消息并更新会话预览 */
    suspend fun delete(messageId: String, ownerId: String) = dao.deleteMessage(messageId, ownerId)
}
