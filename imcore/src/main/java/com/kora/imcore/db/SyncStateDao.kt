package com.kora.imcore.db

class SyncStateDao(private val dbHelper: ImAppDatabaseHelper) {
    fun getCursor(ownerId: String): Long = dbHelper.readableDatabase.rawQuery(
        "SELECT cursor FROM ${ImAppDatabaseHelper.TABLE_SYNC_STATE} WHERE ownerId = ?",
        arrayOf(ownerId)
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
}
