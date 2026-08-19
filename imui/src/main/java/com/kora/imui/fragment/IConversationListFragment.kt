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
 * 会话列表基础 Fragment：
 * 1. 包含微信风格顶部网络连接状态条（自动监听连接状态）。
 * 2. 协程监听本地数据库会话流，自动刷新列表。
 * 3. 业务层通过重写 [resolveTitle]、[resolveAvatar]、[onConversationClick] 实现自定义路由。
 */
abstract class IConversationListFragment : Fragment() {
    private lateinit var adapter: ConversationListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_im_conversation_list, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val empty = view.findViewById<TextView>(R.id.im_conversation_empty)
        val networkBar = view.findViewById<View>(R.id.im_network_state_bar)
        val tvNetworkState = view.findViewById<TextView>(R.id.im_tv_network_state)

        // 点击网络状态条可跳转系统网络设置
        networkBar.setOnClickListener {
            runCatching {
                val intent = android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                startActivity(intent)
            }
        }

        adapter = ConversationListAdapter(requireContext()) { item ->
            viewLifecycleOwner.lifecycleScope.launch {
                IMClient.markConversationRead(item.conversation.sessionId)
                onConversationClick(item.conversation)
            }
        }
        view.findViewById<ListView>(R.id.im_conversation_list).adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 监听 IM 连接状态（类似微信顶部网络断开/重连提示条）
                launch {
                    IMClient.connectionState.collect { connState ->
                        when (connState) {
                            is com.kora.imcore.event.ConnectionState.Connected -> {
                                networkBar.visibility = View.GONE
                            }
                            is com.kora.imcore.event.ConnectionState.Connecting -> {
                                networkBar.visibility = View.VISIBLE
                                networkBar.setBackgroundColor(0xFFF0F5FF.toInt())
                                tvNetworkState.text = "正在连接服务器..."
                                tvNetworkState.setTextColor(0xFF2F54EB.toInt())
                            }
                            is com.kora.imcore.event.ConnectionState.Reconnecting -> {
                                networkBar.visibility = View.VISIBLE
                                networkBar.setBackgroundColor(0xFFFFF7E6.toInt())
                                tvNetworkState.text = "当前网络不稳定，正在重连..."
                                tvNetworkState.setTextColor(0xFFD46B08.toInt())
                            }
                            is com.kora.imcore.event.ConnectionState.Disconnected -> {
                                networkBar.visibility = View.VISIBLE
                                networkBar.setBackgroundColor(0xFFFFF1F0.toInt())
                                tvNetworkState.text = "当前网络不可用，请检查你的网络设置"
                                tvNetworkState.setTextColor(0xFF595959.toInt())
                            }
                            is com.kora.imcore.event.ConnectionState.Failed -> {
                                networkBar.visibility = View.VISIBLE
                                networkBar.setBackgroundColor(0xFFFFF1F0.toInt())
                                tvNetworkState.text = "当前网络不可用，请检查你的网络设置"
                                tvNetworkState.setTextColor(0xFF595959.toInt())
                            }
                        }
                    }
                }

                // 监听会话列表数据
                launch {
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
    }

    protected open fun conversationTitle(conversation: Conversation): String = when (conversation.sessionType) {
        SessionType.P2P -> conversation.peerId
        SessionType.GROUP -> "群聊 ${conversation.sessionId}"
        else -> conversation.sessionId
    }

    protected abstract fun onConversationClick(conversation: Conversation)
}

