package com.kora.imcore.db

import androidx.room.*
import com.zchd.vsports.im.core.constant.MsgStatus
import kotlinx.coroutines.flow.Flow

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/12/21:18:07
 * @Description:
 */
@Dao
interface MessageDao {
    @Transaction
    @Query("SELECT * FROM Message WHERE sessionId = :sessionId ORDER BY id DESC")
    fun getMessageBySessionId(sessionId: String): Flow<List<Message>>


    @Query("SELECT * FROM Message WHERE sessionId = :sessionId ORDER BY id DESC  LIMIT (10) OFFSET (:page)*10")
    suspend fun getMessageBySessionId(sessionId: String, page: Int): List<Message>

    @Transaction
    @Query("SELECT * FROM Message  WHERE sessionId = :sessionId ORDER BY id DESC LIMIT (1)")
    fun getLaseMessageBySessionId(sessionId: String): Flow<Message>

    @Update
    suspend fun updateMessage(vararg message: Message)

    @Query("UPDATE Message SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessage(messageId: String, status: Int = MsgStatus.SUCCESS)

    @Transaction
    @Query("SELECT * FROM Message WHERE messageId = :messageId")
    fun getMessageByMessageId(messageId: String): Message

    @Insert
    suspend fun insertMessage(vararg message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessageList(message: List<Message>)

    @Query("DELETE FROM Message")
    suspend fun deleteAllMessage()
}