package com.kora.im

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kora.imcore.IMClient
import kotlinx.coroutines.launch

class UserListFragment : Fragment() {
    private val currentAccount get() = requireArguments().getString(ARG_ACCOUNT).orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_user_list, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        view.findViewById<TextView>(R.id.current_account).text = "当前登录：${DemoUsers.info(currentAccount)?.nickname}"
        val list = view.findViewById<LinearLayout>(R.id.user_list)
        DemoUsers.accounts.filterNot { it == currentAccount }.forEach { account ->
            list.addView(Button(requireContext()).apply {
                text = "与 ${DemoUsers.info(account)?.nickname} 单聊"
                isAllCaps = false
                setOnClickListener { (activity as MainActivity).openChat(account) }
            })
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    refreshConversations(view)
                    IMClient.messageUpdates.collect { refreshConversations(view) }
                }
                launch {
                    IMClient.incomingMessages.collect { refreshConversations(view) }
                }
            }
        }
    }

    private suspend fun refreshConversations(view: View) {
        val conversations = IMClient.getConversations()
        val container = view.findViewById<LinearLayout>(R.id.conversation_list)
        val empty = view.findViewById<TextView>(R.id.empty_conversations)
        container.removeAllViews()
        empty.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
        conversations.forEach { conversation ->
            container.addView(Button(requireContext()).apply {
                val name = DemoUsers.info(conversation.peerId)?.nickname ?: conversation.peerId
                text = "$name  ·  ${conversation.sessionId.take(12)}…"
                isAllCaps = false
                setOnClickListener { (activity as MainActivity).openChat(conversation.peerId) }
            })
        }
    }

    companion object {
        private const val ARG_ACCOUNT = "account"
        fun newInstance(account: String) = UserListFragment().apply {
            arguments = Bundle().apply { putString(ARG_ACCOUNT, account) }
        }
    }
}
