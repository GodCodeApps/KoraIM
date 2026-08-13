package com.kora.imcore.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConversationDao(private val dbHelper: ImAppDatabaseHelper) {
    suspend fun getAll(ownerId: String): List<Conversation> = withContext(Dispatchers.IO) {
        dbHelper.readableDatabase.rawQuery(
            "SELECT * FROM ${ImAppDatabaseHelper.TABLE_CONVERSATION} " +
                "WHERE ownerId = ? ORDER BY updateTime DESC",
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

    internal fun upsertInTransaction(db: SQLiteDatabase, conversation: Conversation) {
        val values = ContentValues().apply {
            put("sessionId", conversation.sessionId)
            put("sessionType", conversation.sessionType)
            put("ownerId", conversation.ownerId)
            put("peerId", conversation.peerId)
            put("updateTime", conversation.updateTime)
        }
        db.insertWithOnConflict(
            ImAppDatabaseHelper.TABLE_CONVERSATION,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun android.database.Cursor.toConversation() = Conversation(
        id = getLong(getColumnIndexOrThrow("id")),
        sessionId = getString(getColumnIndexOrThrow("sessionId")),
        sessionType = getInt(getColumnIndexOrThrow("sessionType")),
        ownerId = getString(getColumnIndexOrThrow("ownerId")),
        peerId = getString(getColumnIndexOrThrow("peerId")),
        updateTime = getLong(getColumnIndexOrThrow("updateTime"))
    )
}
