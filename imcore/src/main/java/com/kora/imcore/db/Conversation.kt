package com.kora.imcore.db

import com.kora.imcore.constant.SessionType

data class Conversation(
    var id: Long = 0,
    var sessionId: String = "",
    var sessionType: Int = SessionType.None,
    var ownerId: String = "",
    var peerId: String = "",
    var lastMessageId: String = "",
    var lastMessageType: Int = 0,
    var lastMessagePreview: String = "",
    var lastMessageTime: Long = 0L,
    var unreadCount: Int = 0,
    var updateTime: Long = 0L
)
