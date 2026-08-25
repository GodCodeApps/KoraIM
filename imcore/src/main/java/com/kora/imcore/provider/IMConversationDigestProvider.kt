package com.kora.imcore.provider

import com.kora.imcore.db.Message

/** Supplies presentation text for a conversation's latest message. */
fun interface IMConversationDigestProvider {
    fun format(message: Message, ownerId: String): String
}
