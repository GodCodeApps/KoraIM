package com.kora.imcore.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ImAppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "im_app_database.db"
        const val DATABASE_VERSION = 3

        const val TABLE_MESSAGE = "message"
        const val TABLE_USER_INFO = "user_info"
        
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
        const val COLUMN_ACCOUNT = "account"
        
        // User_info columns (some overlap like account, nickname, avatar)
        const val COLUMN_USER_ACCOUNT = "account"
        const val COLUMN_USER_NICKNAME = "nickname"
        const val COLUMN_USER_AVATAR = "avatar"
        const val COLUMN_USER_UPDATE_TIME = "updateTime"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createMessageTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_MESSAGE (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_MESSAGE_ID TEXT NOT NULL,
                $COLUMN_SESSION_TYPE INTEGER NOT NULL,
                $COLUMN_SESSION_ID TEXT NOT NULL,
                $COLUMN_TYPE INTEGER NOT NULL,
                $COLUMN_DIRECT INTEGER NOT NULL,
                $COLUMN_STATUS INTEGER NOT NULL,
                $COLUMN_TIME INTEGER NOT NULL,
                $COLUMN_ATTACHMENT TEXT NOT NULL,
                $COLUMN_EXTRA TEXT NOT NULL,
                $COLUMN_ACCOUNT TEXT NOT NULL
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Simple upgrade strategy: drop and recreate (since Room schema export was false and we don't have migrations)
        // In a real app you might want ALTER TABLE, but we'll follow Room's destructive migration if none provided
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USER_INFO")
        onCreate(db)
    }
}
