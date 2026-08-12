package com.kora.imcore.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kora.imcore.ImSdkImpl

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/12/21:17:55
 * @Description:
 */
@Database(entities = [Message::class], version = 2, exportSchema = false)
abstract class ImAppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: ImAppDatabase? = null
        fun getInstance(context: Context): ImAppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                ImAppDatabase::class.java, "im_${ImSdkImpl.getAccount()}.db"
            ).build()
    }
}