package com.kora.imcore.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.kora.imcore.constant.MsgStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.kora.imcore.netty.SyncEvent
import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.provider.IMConversationDigestProvider
import com.kora.imcore.constant.MsgType
import org.json.JSONObject

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/21:18:07
 * @Description: SQLite implementation of MessageDao
 */
class MessageDao(
    private val dbHelper: ImAppDatabaseHelper,
    private val conversationDao: ConversationDao = ConversationDao(dbHelper),
    var digestProvider: IMConversationDigestProvider? = null
) {

    private fun Message.toConversation(ownerId: String) = Conversation(
        sessionId = sessionId,
        sessionType = sessionType,
        ownerId = ownerId,
        peerId = if (senderId == ownerId) receiverId else senderId,
        lastMessageId = messageId,
        lastMessageType = type,
        lastMessageStatus = status,
        lastMessagePreview = digestProvider?.format(this, ownerId) ?: "[消息]",
        lastMessageTime = time,
        updateTime = time
    )

    private val _tableChangeFlow = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        _tableChangeFlow.tryEmit(Unit)
    }

    private fun notifyChange() {
        _tableChangeFlow.tryEmit(Unit)
    }

    private fun visibleMessageClause(alias: String = ImAppDatabaseHelper.TABLE_MESSAGE): String =
        "NOT EXISTS (SELECT 1 FROM ${ImAppDatabaseHelper.TABLE_MESSAGE_LOCAL_STATE} local " +
            "WHERE local.messageId = $alias.${ImAppDatabaseHelper.COLUMN_MESSAGE_ID} AND local.deleted = 1)"

    private fun isLocallyDeleted(db: SQLiteDatabase, messageId: String): Boolean = db.rawQuery(
        "SELECT 1 FROM ${ImAppDatabaseHelper.TABLE_MESSAGE_LOCAL_STATE} WHERE messageId = ? AND deleted = 1",
        arrayOf(messageId)
    ).use { it.moveToFirst() }

    private fun parseMessageList(cursor: Cursor): List<Message> {
        val list = mutableListOf<Message>()
        if (cursor.moveToFirst()) {
            val idxId = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_ID)
            val idxMessageId = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_MESSAGE_ID)
            val idxSessionType = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_SESSION_TYPE)
            val idxSessionId = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_SESSION_ID)
            val idxType = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_TYPE)
            val idxDirect = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_DIRECT)
            val idxStatus = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_STATUS)
            val idxTime = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_TIME)
            val idxAttachment = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_ATTACHMENT)
            val idxExtra = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_EXTRA)
            val idxSenderId = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_SENDER_ID)
            val idxReceiverId = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_RECEIVER_ID)
            val idxRecalled = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_RECALLED)
            val idxRecalledAt = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_RECALLED_AT)
            val idxRecalledBy = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_RECALLED_BY)

            do {
                val msg = Message()
                msg.id = cursor.getLong(idxId)
                msg.messageId = cursor.getString(idxMessageId)
                msg.sessionType = cursor.getInt(idxSessionType)
                msg.sessionId = cursor.getString(idxSessionId)
                msg.type = cursor.getInt(idxType)
                msg.direct = cursor.getInt(idxDirect)
                msg.status = cursor.getInt(idxStatus)
                msg.time = cursor.getLong(idxTime)
                msg.attachment = cursor.getString(idxAttachment)
                msg.extra = cursor.getString(idxExtra)
                msg.senderId = cursor.getString(idxSenderId)
                msg.receiverId = cursor.getString(idxReceiverId)
                msg.recalled = cursor.getInt(idxRecalled) != 0
                msg.recalledAt = cursor.getLong(idxRecalledAt)
                msg.recalledBy = cursor.getString(idxRecalledBy)
                list.add(msg)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }
    
    private fun messageToContentValues(msg: Message): ContentValues {
        val cv = ContentValues()
        if (msg.id > 0) {
            cv.put(ImAppDatabaseHelper.COLUMN_ID, msg.id)
        }
        cv.put(ImAppDatabaseHelper.COLUMN_MESSAGE_ID, msg.messageId)
        cv.put(ImAppDatabaseHelper.COLUMN_SESSION_TYPE, msg.sessionType)
        cv.put(ImAppDatabaseHelper.COLUMN_SESSION_ID, msg.sessionId)
        cv.put(ImAppDatabaseHelper.COLUMN_TYPE, msg.type)
        cv.put(ImAppDatabaseHelper.COLUMN_DIRECT, msg.direct)
        cv.put(ImAppDatabaseHelper.COLUMN_STATUS, msg.status)
        cv.put(ImAppDatabaseHelper.COLUMN_TIME, msg.time)
        cv.put(ImAppDatabaseHelper.COLUMN_ATTACHMENT, msg.attachment)
        cv.put(ImAppDatabaseHelper.COLUMN_EXTRA, msg.extra)
        cv.put(ImAppDatabaseHelper.COLUMN_SENDER_ID, msg.senderId)
        cv.put(ImAppDatabaseHelper.COLUMN_RECEIVER_ID, msg.receiverId)
        cv.put(ImAppDatabaseHelper.COLUMN_RECALLED, if (msg.recalled) 1 else 0)
        cv.put(ImAppDatabaseHelper.COLUMN_RECALLED_AT, msg.recalledAt)
        cv.put(ImAppDatabaseHelper.COLUMN_RECALLED_BY, msg.recalledBy)
        return cv
    }

    fun getMessageBySessionId(sessionId: String): Flow<List<Message>> {
        return _tableChangeFlow.map {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                "SELECT * FROM ${ImAppDatabaseHelper.TABLE_MESSAGE} WHERE sessionId = ? AND ${visibleMessageClause()} ORDER BY id DESC",
                arrayOf(sessionId)
            )
            parseMessageList(cursor)
        }
    }

    fun getP2PMessages(ownerId: String, peerId: String): Flow<List<Message>> = _tableChangeFlow.map {
        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT * FROM ${ImAppDatabaseHelper.TABLE_MESSAGE} m WHERE sessionType = ? AND " +
                "((senderId = ? AND receiverId = ?) OR (senderId = ? AND receiverId = ?)) " +
                "AND ${visibleMessageClause("m")} " +
                "ORDER BY id DESC",
            arrayOf(
                com.kora.imcore.constant.SessionType.P2P.toString(),
                ownerId, peerId, peerId, ownerId
            )
        )
        parseMessageList(cursor)
    }

    suspend fun confirmMessage(message: Message, ownerId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val existed = db.rawQuery("SELECT 1 FROM ${ImAppDatabaseHelper.TABLE_MESSAGE} WHERE messageId = ?", arrayOf(message.messageId)).use { it.moveToFirst() }
            db.insertWithOnConflict(
                ImAppDatabaseHelper.TABLE_MESSAGE,
                null,
                messageToContentValues(message),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            if (!isLocallyDeleted(db, message.messageId)) {
                conversationDao.upsertInTransaction(
                    db,
                    message.toConversation(ownerId),
                    incrementUnread = !existed && message.direct == MsgDirection.IN
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        conversationDao.notifyChanged()
        notifyChange()
    }

    internal suspend fun applySync(ownerId: String, events: List<SyncEvent>, cursor: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            events.forEach { event ->
                val message = event.payload ?: return@forEach
                if (event.eventType == "recall") {
                    val values = ContentValues().apply {
                        put(ImAppDatabaseHelper.COLUMN_RECALLED, 1)
                        put(ImAppDatabaseHelper.COLUMN_RECALLED_AT, message.recalledAt)
                        put(ImAppDatabaseHelper.COLUMN_RECALLED_BY, message.recalledBy)
                    }
                    db.update(ImAppDatabaseHelper.TABLE_MESSAGE, values,
                        "${ImAppDatabaseHelper.COLUMN_MESSAGE_ID} = ?", arrayOf(message.messageId))
                    val isLatest = db.rawQuery(
                        "SELECT 1 FROM ${ImAppDatabaseHelper.TABLE_CONVERSATION} WHERE ownerId = ? AND sessionId = ? AND lastMessageId = ?",
                        arrayOf(ownerId, message.sessionId, message.messageId)
                    ).use { it.moveToFirst() }
                    if (isLatest) conversationDao.upsertInTransaction(db, message.toConversation(ownerId), false, forceUpdate = true)
                    return@forEach
                }
                message.id = 0
                message.status = MsgStatus.SUCCESS
                message.direct = if (message.senderId == ownerId) {
                    com.kora.imcore.constant.MsgDirection.OUT
                } else {
                    com.kora.imcore.constant.MsgDirection.IN
                }
                val inserted = db.insertWithOnConflict(
                    ImAppDatabaseHelper.TABLE_MESSAGE,
                    null,
                    messageToContentValues(message),
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (!isLocallyDeleted(db, message.messageId)) {
                    conversationDao.upsertInTransaction(db, message.toConversation(ownerId), inserted != -1L && message.direct == MsgDirection.IN)
                }
            }
            db.insertWithOnConflict(
                ImAppDatabaseHelper.TABLE_SYNC_STATE,
                null,
                ContentValues().apply {
                    put("ownerId", ownerId)
                    put("cursor", cursor)
                    put("updateTime", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (events.isNotEmpty()) {
            conversationDao.notifyChanged()
            notifyChange()
        }
    }

    suspend fun getMessageBySessionId(sessionId: String, page: Int): List<Message> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val offset = page * 10
        val cursor = db.rawQuery(
            "SELECT * FROM ${ImAppDatabaseHelper.TABLE_MESSAGE} WHERE sessionId = ? AND ${visibleMessageClause()} ORDER BY id DESC LIMIT 10 OFFSET ?",
            arrayOf(sessionId, offset.toString())
        )
        parseMessageList(cursor)
    }

    fun getLaseMessageBySessionId(sessionId: String): Flow<Message> {
        return _tableChangeFlow.map {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                "SELECT * FROM ${ImAppDatabaseHelper.TABLE_MESSAGE} WHERE sessionId = ? AND ${visibleMessageClause()} ORDER BY id DESC LIMIT 1",
                arrayOf(sessionId)
            )
            val list = parseMessageList(cursor)
            list.firstOrNull() ?: Message()
        }
    }

    suspend fun updateMessage(vararg message: Message) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (msg in message) {
                db.update(
                    ImAppDatabaseHelper.TABLE_MESSAGE,
                    messageToContentValues(msg),
                    "id = ?",
                    arrayOf(msg.id.toString())
                )
            }
            db.setTransactionSuccessful()
            notifyChange()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun updateMessage(messageId: String, status: Int = MsgStatus.SUCCESS) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(ImAppDatabaseHelper.COLUMN_STATUS, status)
        }
        db.update(
            ImAppDatabaseHelper.TABLE_MESSAGE,
            cv,
            "messageId = ?",
            arrayOf(messageId)
        )
        notifyChange()
    }

    suspend fun markRecalled(message: Message, ownerId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.update(ImAppDatabaseHelper.TABLE_MESSAGE, ContentValues().apply {
                put(ImAppDatabaseHelper.COLUMN_RECALLED, 1)
                put(ImAppDatabaseHelper.COLUMN_RECALLED_AT, message.recalledAt)
                put(ImAppDatabaseHelper.COLUMN_RECALLED_BY, message.recalledBy)
            }, "${ImAppDatabaseHelper.COLUMN_MESSAGE_ID} = ?", arrayOf(message.messageId))
            val isLatest = db.rawQuery(
                "SELECT 1 FROM ${ImAppDatabaseHelper.TABLE_CONVERSATION} WHERE ownerId = ? AND sessionId = ? AND lastMessageId = ?",
                arrayOf(ownerId, message.sessionId, message.messageId)
            ).use { it.moveToFirst() }
            if (isLatest) conversationDao.upsertInTransaction(db, message.toConversation(ownerId), false, forceUpdate = true)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        conversationDao.notifyChanged()
        notifyChange()
    }

    fun getMessageByMessageId(messageId: String): Message? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM ${ImAppDatabaseHelper.TABLE_MESSAGE} WHERE messageId = ? AND ${visibleMessageClause()}",
            arrayOf(messageId)
        )
        return parseMessageList(cursor).firstOrNull()
    }

    /** Mark messages left in SENDING after a previous process death as failed. */
    fun markStaleOutgoingAsFailed(ownerId: String): Int {
        val db = dbHelper.writableDatabase
        var changed = 0
        db.beginTransaction()
        try {
            changed = db.update(
                ImAppDatabaseHelper.TABLE_MESSAGE,
                ContentValues().apply { put(ImAppDatabaseHelper.COLUMN_STATUS, MsgStatus.FAIL) },
                "${ImAppDatabaseHelper.COLUMN_DIRECT} = ? AND ${ImAppDatabaseHelper.COLUMN_STATUS} = ? " +
                    "AND ${ImAppDatabaseHelper.COLUMN_SENDER_ID} = ?",
                arrayOf(MsgDirection.OUT.toString(), MsgStatus.SENDING.toString(), ownerId)
            )
            // Keep the conversation preview consistent with the message row.
            db.execSQL(
                "UPDATE ${ImAppDatabaseHelper.TABLE_CONVERSATION} SET lastMessageStatus = ? " +
                    "WHERE ownerId = ? AND lastMessageId IN (SELECT messageId FROM ${ImAppDatabaseHelper.TABLE_MESSAGE} " +
                    "WHERE direct = ? AND status = ? AND senderId = ?)",
                arrayOf(
                    MsgStatus.FAIL.toString(), ownerId,
                    MsgDirection.OUT.toString(), MsgStatus.FAIL.toString(), ownerId
                )
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            conversationDao.notifyChanged()
            notifyChange()
        }
        return changed
    }

    suspend fun insertMessage(vararg message: Message) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (msg in message) {
                db.insertWithOnConflict(
                    ImAppDatabaseHelper.TABLE_MESSAGE,
                    null,
                    messageToContentValues(msg),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
            notifyChange()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun insertMessageList(messageList: List<Message>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (msg in messageList) {
                // Room had REPLACE conflict strategy here
                db.insertWithOnConflict(
                    ImAppDatabaseHelper.TABLE_MESSAGE,
                    null,
                    messageToContentValues(msg),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
            notifyChange()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun deleteMessage(messageId: String, ownerId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            var sessionId: String? = null
            db.rawQuery(
                "SELECT ${ImAppDatabaseHelper.COLUMN_SESSION_ID} FROM ${ImAppDatabaseHelper.TABLE_MESSAGE} WHERE ${ImAppDatabaseHelper.COLUMN_MESSAGE_ID} = ?",
                arrayOf(messageId)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    sessionId = cursor.getString(0)
                }
            }

            if (!sessionId.isNullOrEmpty()) {
                db.insertWithOnConflict(
                    ImAppDatabaseHelper.TABLE_MESSAGE_LOCAL_STATE,
                    null,
                    ContentValues().apply {
                        put("ownerId", ownerId)
                        put("messageId", messageId)
                        put("sessionId", sessionId)
                        put("deleted", 1)
                        put("deletedAt", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            if (!sessionId.isNullOrEmpty()) {
                val isLatestInConversation = db.rawQuery(
                    "SELECT 1 FROM ${ImAppDatabaseHelper.TABLE_CONVERSATION} WHERE ownerId = ? AND sessionId = ? AND lastMessageId = ?",
                    arrayOf(ownerId, sessionId, messageId)
                ).use { it.moveToFirst() }

                if (isLatestInConversation) {
                    val remainingLatestCursor = db.rawQuery(
                        "SELECT * FROM ${ImAppDatabaseHelper.TABLE_MESSAGE} WHERE ${ImAppDatabaseHelper.COLUMN_SESSION_ID} = ? " +
                            "AND ${visibleMessageClause()} ORDER BY ${ImAppDatabaseHelper.COLUMN_ID} DESC LIMIT 1",
                        arrayOf(sessionId)
                    )
                    val remainingMessages = parseMessageList(remainingLatestCursor)
                    if (remainingMessages.isNotEmpty()) {
                        conversationDao.upsertInTransaction(
                            db,
                            remainingMessages.first().toConversation(ownerId),
                            incrementUnread = false,
                            forceUpdate = true
                        )
                    } else {
                        val cv = ContentValues().apply {
                            put("lastMessageId", "")
                            put("lastMessageType", 0)
                            put("lastMessagePreview", "")
                            put("lastMessageTime", 0L)
                            put("updateTime", System.currentTimeMillis())
                        }
                        db.update(
                            ImAppDatabaseHelper.TABLE_CONVERSATION,
                            cv,
                            "ownerId = ? AND sessionId = ?",
                            arrayOf(ownerId, sessionId)
                        )
                    }
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        conversationDao.notifyChanged()
        notifyChange()
    }

    suspend fun getDeletedMessageCount(sessionId: String, ownerId: String): Int = withContext(Dispatchers.IO) {
        dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${ImAppDatabaseHelper.TABLE_MESSAGE_LOCAL_STATE} " +
                "WHERE ownerId = ? AND sessionId = ? AND deleted = 1",
            arrayOf(ownerId, sessionId)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    suspend fun restoreDeletedMessages(sessionId: String, ownerId: String): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        var restored = 0
        db.beginTransaction()
        try {
            restored = db.delete(
                ImAppDatabaseHelper.TABLE_MESSAGE_LOCAL_STATE,
                "ownerId = ? AND sessionId = ? AND deleted = 1",
                arrayOf(ownerId, sessionId)
            )
            if (restored > 0) refreshConversationLatest(db, ownerId, sessionId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (restored > 0) {
            conversationDao.notifyChanged()
            notifyChange()
        }
        restored
    }

    private fun refreshConversationLatest(db: SQLiteDatabase, ownerId: String, sessionId: String) {
        val latest = parseMessageList(db.rawQuery(
            "SELECT * FROM ${ImAppDatabaseHelper.TABLE_MESSAGE} WHERE ${ImAppDatabaseHelper.COLUMN_SESSION_ID} = ? " +
                "AND ${visibleMessageClause()} ORDER BY ${ImAppDatabaseHelper.COLUMN_ID} DESC LIMIT 1",
            arrayOf(sessionId)
        )).firstOrNull()
        if (latest != null) {
            conversationDao.upsertInTransaction(db, latest.toConversation(ownerId), false, forceUpdate = true)
        } else {
            db.update(
                ImAppDatabaseHelper.TABLE_CONVERSATION,
                ContentValues().apply {
                    put("lastMessageId", "")
                    put("lastMessageType", 0)
                    put("lastMessageStatus", MsgStatus.SUCCESS)
                    put("lastMessagePreview", "")
                    put("lastMessageTime", 0L)
                    put("updateTime", System.currentTimeMillis())
                },
                "ownerId = ? AND sessionId = ?",
                arrayOf(ownerId, sessionId)
            )
        }
    }

    suspend fun deleteAllMessage() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(ImAppDatabaseHelper.TABLE_MESSAGE, null, null)
        db.delete(ImAppDatabaseHelper.TABLE_MESSAGE_LOCAL_STATE, null, null)
        notifyChange()
    }
}
