package com.kora.imcore.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

class ImAppDatabaseHelper(context: Context, account: String) :
    SQLiteOpenHelper(context, databaseNameFor(account), null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_PREFIX = "im_app_database_"
        const val DATABASE_VERSION = 8

        const val TABLE_MESSAGE = "message"
        const val TABLE_USER_INFO = "user_info"
        const val TABLE_CONVERSATION = "conversation"
        const val TABLE_SYNC_STATE = "sync_state"
        
        // Table columns
        const val COLUMN_ID = "id"
        const val COLUMN_MESSAGE_ID = "messageId"
        const val COLUMN_SESSION_TYPE = "sessionType"
        const val COLUMN_SESSION_ID = "sessionId"
        const val COLUMN_TYPE = "type"
        const val COLUMN_DIRECT = "direct"
        const val COLUMN_STATUS = "status"
        const val COLUMN_TIME = "time"
        const val COLUMN_ATTACHMENT = "attachment"
        const val COLUMN_EXTRA = "extra"
        const val COLUMN_SENDER_ID = "senderId"
        const val COLUMN_RECEIVER_ID = "receiverId"
        const val COLUMN_RECALLED = "recalled"
        const val COLUMN_RECALLED_AT = "recalledAt"
        const val COLUMN_RECALLED_BY = "recalledBy"
        
        // User_info columns (some overlap like account, nickname, avatar)
        const val COLUMN_USER_ACCOUNT = "account"
        const val COLUMN_USER_NICKNAME = "nickname"
        const val COLUMN_USER_AVATAR = "avatar"
        const val COLUMN_USER_UPDATE_TIME = "updateTime"

        fun databaseNameFor(account: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(account.toByteArray(Charsets.UTF_8))
            val hash = digest.joinToString("") { "%02x".format(it) }
            return "$DATABASE_PREFIX$hash.db"
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createMessageTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_MESSAGE (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_MESSAGE_ID TEXT NOT NULL UNIQUE,
                $COLUMN_SESSION_TYPE INTEGER NOT NULL,
                $COLUMN_SESSION_ID TEXT NOT NULL,
                $COLUMN_TYPE INTEGER NOT NULL,
                $COLUMN_DIRECT INTEGER NOT NULL,
                $COLUMN_STATUS INTEGER NOT NULL,
                $COLUMN_TIME INTEGER NOT NULL,
                $COLUMN_ATTACHMENT TEXT NOT NULL,
                $COLUMN_EXTRA TEXT NOT NULL,
                $COLUMN_SENDER_ID TEXT NOT NULL,
                $COLUMN_RECEIVER_ID TEXT NOT NULL,
                $COLUMN_RECALLED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_RECALLED_AT INTEGER NOT NULL DEFAULT 0,
                $COLUMN_RECALLED_BY TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent()
        
        val createUserTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_USER_INFO (
                $COLUMN_USER_ACCOUNT TEXT PRIMARY KEY NOT NULL,
                $COLUMN_USER_NICKNAME TEXT NOT NULL,
                $COLUMN_USER_AVATAR TEXT NOT NULL,
                $COLUMN_USER_UPDATE_TIME INTEGER NOT NULL
            )
        """.trimIndent()
        
        db.execSQL(createMessageTable)
        db.execSQL(createUserTable)
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS $TABLE_CONVERSATION (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessionId TEXT NOT NULL,
                sessionType INTEGER NOT NULL,
                ownerId TEXT NOT NULL,
                peerId TEXT NOT NULL DEFAULT '',
                lastMessageId TEXT NOT NULL DEFAULT '',
                lastMessageType INTEGER NOT NULL DEFAULT 0,
                lastMessageStatus INTEGER NOT NULL DEFAULT 0,
                lastMessagePreview TEXT NOT NULL DEFAULT '',
                lastMessageTime INTEGER NOT NULL DEFAULT 0,
                unreadCount INTEGER NOT NULL DEFAULT 0,
                updateTime INTEGER NOT NULL,
                UNIQUE(ownerId, sessionId),
                UNIQUE(ownerId, sessionType, peerId)
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS $TABLE_SYNC_STATE (
                ownerId TEXT PRIMARY KEY NOT NULL,
                cursor INTEGER NOT NULL,
                updateTime INTEGER NOT NULL
            )""".trimIndent()
        )
        createIndexes(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 7) {
            runCatching {
                db.execSQL("ALTER TABLE $TABLE_CONVERSATION ADD COLUMN lastMessageStatus INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_message_session_time " +
                "ON $TABLE_MESSAGE($COLUMN_SESSION_ID, $COLUMN_TIME DESC)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_message_status ON $TABLE_MESSAGE($COLUMN_STATUS)"
        )
    }
}
