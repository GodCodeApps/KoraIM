package com.kora.im

import com.kora.imcore.db.Conversation
import com.kora.imui.fragment.IConversationListFragment

class DemoConversationListFragment : IConversationListFragment() {
    override fun onConversationClick(conversation: Conversation) {
        (activity as? MainActivity)?.openChat(conversation.peerId)
    }
}
