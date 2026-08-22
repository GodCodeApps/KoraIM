package com.kora.imcore.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn

class ConversationDao(private val dbHelper: ImAppDatabaseHelper) {
    private val changes = MutableStateFlow(0L)

    fun observeAll(ownerId: String): Flow<List<Conversation>> =
        changes.map { queryAll(ownerId) }.flowOn(Dispatchers.IO)

    suspend fun getAll(ownerId: String): List<Conversation> = withContext(Dispatchers.IO) {
        queryAll(ownerId)
    }

    private fun queryAll(ownerId: String): List<Conversation> {
        return dbHelper.readableDatabase.rawQuery(
            "SELECT * FROM ${ImAppDatabaseHelper.TABLE_CONVERSATION} " +
                "WHERE ownerId = ? ORDER BY lastMessageTime DESC, updateTime DESC",
            arrayOf(ownerId)
        ).use { cursor ->
            val conversations = mutableListOf<Conversation>()
            while (cursor.moveToNext()) {
                conversations += cursor.toConversation()
            }
            conversations
        }
    }

    suspend fun findP2P(ownerId: String, peerId: String): Conversation? = withContext(Dispatchers.IO) {
        dbHelper.readableDatabase.rawQuery(
            "SELECT * FROM ${ImAppDatabaseHelper.TABLE_CONVERSATION} " +
                "WHERE ownerId = ? AND sessionType = ? AND peerId = ? LIMIT 1",
            arrayOf(ownerId, com.kora.imcore.constant.SessionType.P2P.toString(), peerId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            cursor.toConversation()
        }
    }

    internal fun upsertInTransaction(db: SQLiteDatabase, conversation: Conversation, incrementUnread: Boolean = false, forceUpdate: Boolean = false) {
        val previous = db.rawQuery(
            "SELECT * FROM ${ImAppDatabaseHelper.TABLE_CONVERSATION} WHERE ownerId = ? AND sessionId = ? LIMIT 1",
            arrayOf(conversation.ownerId, conversation.sessionId)
        ).use { if (it.moveToFirst()) it.toConversation() else null }
        // 如果是强制更新(如删除最新消息回滚)，或是更新的消息、或者是对当前最新同一条消息的状态变更
        val isNewerOrSameMessage = forceUpdate || previous == null ||
            conversation.lastMessageTime >= previous.lastMessageTime ||
            conversation.lastMessageId == previous.lastMessageId
        val latest = if (isNewerOrSameMessage) conversation else previous
        val values = ContentValues().apply {
            put("sessionId", latest.sessionId)
            put("sessionType", latest.sessionType)
            put("ownerId", latest.ownerId)
            put("peerId", latest.peerId)
            put("lastMessageId", latest.lastMessageId)
            put("lastMessageType", latest.lastMessageType)
            put("lastMessageStatus", latest.lastMessageStatus)
            put("lastMessagePreview", latest.lastMessagePreview)
            put("lastMessageTime", latest.lastMessageTime)
            put("unreadCount", (previous?.unreadCount ?: 0) + if (incrementUnread) 1 else 0)
            put("updateTime", maxOf(previous?.updateTime ?: 0L, conversation.updateTime))
        }
        db.insertWithOnConflict(
            ImAppDatabaseHelper.TABLE_CONVERSATION,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    suspend fun markRead(ownerId: String, sessionId: String) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.update(
            ImAppDatabaseHelper.TABLE_CONVERSATION,
            ContentValues().apply { put("unreadCount", 0) },
            "ownerId = ? AND sessionId = ?",
            arrayOf(ownerId, sessionId)
        )
        notifyChanged()
    }

    suspend fun delete(ownerId: String, sessionId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                ImAppDatabaseHelper.TABLE_CONVERSATION,
                "ownerId = ? AND sessionId = ?",
                arrayOf(ownerId, sessionId)
            )
            db.delete(
                ImAppDatabaseHelper.TABLE_MESSAGE,
                "sessionId = ?",
                arrayOf(sessionId)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    internal fun notifyChanged() { changes.value = changes.value + 1 }

    private fun android.database.Cursor.toConversation() = Conversation(
        id = getLong(getColumnIndexOrThrow("id")),
        sessionId = getString(getColumnIndexOrThrow("sessionId")),
        sessionType = getInt(getColumnIndexOrThrow("sessionType")),
        ownerId = getString(getColumnIndexOrThrow("ownerId")),
        peerId = getString(getColumnIndexOrThrow("peerId")),
        lastMessageId = getString(getColumnIndexOrThrow("lastMessageId")),
        lastMessageType = getInt(getColumnIndexOrThrow("lastMessageType")),
        lastMessageStatus = getColumnIndex("lastMessageStatus").takeIf { it >= 0 }?.let { getInt(it) } ?: com.kora.imcore.constant.MsgStatus.SUCCESS,
        lastMessagePreview = getString(getColumnIndexOrThrow("lastMessagePreview")),
        lastMessageTime = getLong(getColumnIndexOrThrow("lastMessageTime")),
        unreadCount = getInt(getColumnIndexOrThrow("unreadCount")),
        updateTime = getLong(getColumnIndexOrThrow("updateTime"))
    )
}
