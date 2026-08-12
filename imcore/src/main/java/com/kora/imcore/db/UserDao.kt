package com.kora.imcore.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserDao(private val dbHelper: ImAppDatabaseHelper) {

    private fun parseUserInfoList(cursor: Cursor): List<UserInfo> {
        val list = mutableListOf<UserInfo>()
        if (cursor.moveToFirst()) {
            val idxAccount = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_USER_ACCOUNT)
            val idxNickname = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_USER_NICKNAME)
            val idxAvatar = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_USER_AVATAR)
            val idxUpdateTime = cursor.getColumnIndexOrThrow(ImAppDatabaseHelper.COLUMN_USER_UPDATE_TIME)

            do {
                val account = cursor.getString(idxAccount)
                val nickname = cursor.getString(idxNickname)
                val avatar = cursor.getString(idxAvatar)
                val updateTime = cursor.getLong(idxUpdateTime)
                list.add(UserInfo(account, nickname, avatar, updateTime))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }
    
    private fun userInfoToContentValues(userInfo: UserInfo): ContentValues {
        val cv = ContentValues()
        cv.put(ImAppDatabaseHelper.COLUMN_USER_ACCOUNT, userInfo.account)
        cv.put(ImAppDatabaseHelper.COLUMN_USER_NICKNAME, userInfo.nickname)
        cv.put(ImAppDatabaseHelper.COLUMN_USER_AVATAR, userInfo.avatar)
        cv.put(ImAppDatabaseHelper.COLUMN_USER_UPDATE_TIME, userInfo.updateTime)
        return cv
    }

    fun getUserInfo(account: String): UserInfo? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM ${ImAppDatabaseHelper.TABLE_USER_INFO} WHERE ${ImAppDatabaseHelper.COLUMN_USER_ACCOUNT} = ?",
            arrayOf(account)
        )
        return parseUserInfoList(cursor).firstOrNull()
    }

    suspend fun insertOrUpdateUserInfo(userInfo: UserInfo) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.insertWithOnConflict(
            ImAppDatabaseHelper.TABLE_USER_INFO,
            null,
            userInfoToContentValues(userInfo),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }
    
    suspend fun insertOrUpdateUserInfoList(userInfoList: List<UserInfo>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (userInfo in userInfoList) {
                db.insertWithOnConflict(
                    ImAppDatabaseHelper.TABLE_USER_INFO,
                    null,
                    userInfoToContentValues(userInfo),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
