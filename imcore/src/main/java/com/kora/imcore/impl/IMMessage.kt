package com.kora.imcore.impl

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.db.Message
import java.io.Serializable

/**
 * IM 消息的公开接口，上层 App 需要实现此接口来创建自定义消息对象。
 *
 * 设计说明：
 * - 继承 [Serializable] 以支持 Intent/Bundle 传递
 * - [getMessage] 将业务消息转换为数据库实体 [Message]
 * - 状态可通过 [setMsgStatus] 外部更新（如发送成功/失败时）
 */
interface IMMessage : Serializable {
    /** 发送者账号 */
    val senderId: String
    /** 接收者账号（P2P）或群组 ID（群聊） */
    val receiverId: String

    /** 消息唯一标识（客户端生成的 UUID） */
    fun getMsgId(): String
    /** 会话类型（P2P/群聊/聊天室等，参见 SessionType） */
    fun getIMSessionType(): Int
    /** 会话 ID（首次发送时可为空，服务端分配后回填） */
    fun getIMSessionId(): String
    /** 消息类型（文本/图片/语音等，参见 MsgType） */
    fun getMsgType(): Int
    /** 消息方向（发出/收到，参见 MsgDirection） */
    fun getMsgDirection(): Int
    /** 消息状态（发送中/成功/失败等，参见 MsgStatus） */
    fun getMsgStatus(): Int
    /** 消息时间戳（毫秒） */
    fun getMsgTime(): Long
    /** 消息扩展字段（JSON 格式的自定义数据） */
    fun getMsgExtra(): String
    /** 消息附件（图片/语音/视频等自定义附件） */
    fun getAttachment(): MsgAttachment?
    /** 转换为数据库实体对象 */
    fun getMessage(): Message
    /** 更新消息状态 */
    fun setMsgStatus(status: Int)
}
