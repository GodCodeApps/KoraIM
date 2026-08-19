package com.kora.imcore.db

import com.kora.imcore.constant.MsgStatus
import com.kora.imcore.constant.SessionType

/**
 * 会话实体类，代表会话列表中的一个会话项（如单聊、群聊）。
 *
 * 字段说明：
 * @property id 自增主键
 * @property sessionId 会话唯一标识
 * @property sessionType 会话类型：[SessionType.P2P] 单聊、[SessionType.GROUP] 群聊
 * @property ownerId 属于哪个用户的会话（当前登录账号 ID）
 * @property peerId 对端用户 ID（单聊时为对方账号，群聊时为空）
 * @property lastMessageId 最新一条消息的 ID
 * @property lastMessageType 最新一条消息的类型（如文本、图片、语音等，对应 [MsgType]）
 * @property lastMessageStatus 最新一条消息的状态（发送中、成功、失败，对应 [MsgStatus]）
 * @property lastMessagePreview 会话列表中显示的最新消息预览文案（如 "[图片]"、"你好"）
 * @property lastMessageTime 最新一条消息的发送时间戳（毫秒）
 * @property unreadCount 未读消息数
 * @property updateTime 会话更新时间戳
 */
data class Conversation(
    var id: Long = 0,
    var sessionId: String = "",
    var sessionType: Int = SessionType.None,
    var ownerId: String = "",
    var peerId: String = "",
    var lastMessageId: String = "",
    var lastMessageType: Int = 0,
    var lastMessageStatus: Int = MsgStatus.SUCCESS,
    var lastMessagePreview: String = "",
    var lastMessageTime: Long = 0L,
    var unreadCount: Int = 0,
    var updateTime: Long = 0L
)
