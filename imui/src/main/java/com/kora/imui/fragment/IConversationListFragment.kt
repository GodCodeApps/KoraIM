package com.kora.imui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kora.imcore.IMClient
import com.kora.imcore.constant.SessionType
import com.kora.imcore.db.Conversation
import com.kora.imui.R
import com.kora.imui.adapter.ConversationListAdapter
import com.kora.imui.adapter.ConversationListItem
import kotlinx.coroutines.launch

/**
 * Reusable conversation list UI. The host app subclasses it to own navigation,
 * while profile lookup and conversation persistence remain in the SDK.
 */
abstract class IConversationListFragment : Fragment() {
    private lateinit var adapter: ConversationListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_im_conversation_list, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val empty = view.findViewById<TextView>(R.id.im_conversation_empty)
        adapter = ConversationListAdapter(requireContext()) { item ->
            viewLifecycleOwner.lifecycleScope.launch {
                IMClient.markConversationRead(item.conversation.sessionId)
                onConversationClick(item.conversation)
            }
        }
        view.findViewById<ListView>(R.id.im_conversation_list).adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                IMClient.observeConversations().collect { conversations ->
                    val items = conversations.map { conversation ->
                        val profile = if (conversation.sessionType == SessionType.P2P) {
                            IMClient.getUserInfo(conversation.peerId)
                        } else null
                        ConversationListItem(
                            conversation = conversation,
                            title = profile?.nickname?.takeIf(String::isNotBlank)
                                ?: conversationTitle(conversation),
                            avatar = profile?.avatar.orEmpty()
                        )
                    }
                    adapter.submit(items)
                    empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    protected open fun conversationTitle(conversation: Conversation): String = when (conversation.sessionType) {
        SessionType.P2P -> conversation.peerId
        SessionType.GROUP -> "群聊 ${conversation.sessionId}"
        else -> conversation.sessionId
    }

    protected abstract fun onConversationClick(conversation: Conversation)
}

