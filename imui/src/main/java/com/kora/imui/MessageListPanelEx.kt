package com.kora.imui

import android.content.Context
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.kora.imcore.IMClient
import com.kora.imcore.impl.IMMessage
import com.kora.imui.adapter.MsgListAdapter
import kotlinx.coroutines.launch

/**
 * 聊天消息列表控制器：
 * 1. 管理 RecyclerView 消息列表与适配器。
 * 2. 采用 ReverseLayout 模式布局，监听协程 Flow 实时刷新消息。
 * 3. 智能判断滚动位置并在新消息或键盘弹出时自动重锚定到底部。
 */
class MessageListPanelEx(
    var context: Fragment,
    var sessionId: String,
    var peerId: String,
    var rootView: View,
    var remote: Boolean
) {
    private var recyclerVew = rootView.findViewById<RecyclerView>(R.id.rv_messages)
    private var edInputText = rootView.findViewById<AppCompatEditText>(R.id.et_message)

    private var mMessageAdapter: MsgListAdapter? = null
    private var mPage = 0
    private var mLinearLayoutManager: LinearLayoutManager? = null
    private var userNearBottom = true
    private var lastRecyclerHeight = 0
    init {
        mPage = 0
        mMessageAdapter = MsgListAdapter()
        recyclerVew?.setHasFixedSize(true)
        mLinearLayoutManager =
            LinearLayoutManager(recyclerVew.context, LinearLayoutManager.VERTICAL, true)
        recyclerVew.layoutManager = mLinearLayoutManager
        recyclerVew.adapter = mMessageAdapter
        recyclerVew.itemAnimator?.changeDuration = 0
        recyclerVew.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                userNearBottom = isNearBottom()
            }
        })
        recyclerVew.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val newHeight = bottom - top
            val oldHeight = oldBottom - oldTop
            if (lastRecyclerHeight == 0) lastRecyclerHeight = oldHeight
            if (newHeight != oldHeight && userNearBottom) {
                // The IME and the emoji/more panels resize the message viewport.
                // Re-anchor only when the user was already reading the latest message.
                scrollToBottom()
            }
            lastRecyclerHeight = newHeight
        }
        observeMessages()
//        refreshLayout?.setOnRefreshListener {
//            mPage++
//            loadMessageHistory()
//        }
        /**
         * 监听键盘弹起
         */
        var imm =
            context.context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        recyclerVew.setOnTouchListener { v, event ->
            imm.hideSoftInputFromWindow(edInputText.windowToken, 0)
            false
        }
    }

    private fun observeMessages() {
        context.viewLifecycleOwner.lifecycleScope.launch {
            context.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val messages = if (peerId.isNotBlank()) {
                    IMClient.observeP2PMessages(peerId)
                } else {
                    IMClient.observeMessages(sessionId)
                }
                messages.collect {
                    Log.e("loadMessageHistory", it.toString())
                    val wasEmpty = (mMessageAdapter?.itemCount ?: 0) == 0
                    val shouldScrollToBottom = wasEmpty || userNearBottom
                    val anchorPosition = mLinearLayoutManager?.findFirstVisibleItemPosition() ?: -1
                    val anchorMessageId = if (!shouldScrollToBottom && anchorPosition >= 0) {
                        mMessageAdapter?.getMessageId(anchorPosition)
                    } else null
                    // The list uses reverseLayout=true, so LinearLayoutManager interprets
                    // the offset from the resolved end (the RecyclerView bottom), not top.
                    val anchorOffset = if (anchorPosition >= 0) {
                        val anchorView = mLinearLayoutManager?.findViewByPosition(anchorPosition)
                        if (anchorView != null) {
                            recyclerVew.height - recyclerVew.paddingBottom - anchorView.bottom
                        } else 0
                    } else 0

                    mMessageAdapter?.replaceAll(it)
                    if (shouldScrollToBottom) {
                        scrollToBottom()
                    } else if (anchorMessageId != null) {
                        recyclerVew.post {
                            val newPosition = mMessageAdapter?.indexOfMessage(anchorMessageId) ?: -1
                            if (newPosition >= 0) {
                                mLinearLayoutManager?.scrollToPositionWithOffset(newPosition, anchorOffset)
                            }
                        }
                    }
                }
            }
        }
    }

    fun addItemMsg(message: IMMessage) {
        mMessageAdapter?.addItem(message)
        userNearBottom = true
        scrollToBottom()
    }

    fun scrollToMessage(messageId: String): Boolean {
        val position = mMessageAdapter?.indexOfMessage(messageId) ?: -1
        if (position < 0) return false
        mLinearLayoutManager?.scrollToPositionWithOffset(position, recyclerVew.height / 3)
        recyclerVew.post {
            recyclerVew.findViewHolderForAdapterPosition(position)?.itemView?.apply {
                alpha = 0.45f
                animate().alpha(1f).setDuration(450L).start()
            }
        }
        return true
    }

    private fun isNearBottom(): Boolean {
        val firstVisible = mLinearLayoutManager?.findFirstVisibleItemPosition() ?: return true
        return firstVisible <= 1
    }

    private fun scrollToBottom() {
        recyclerVew?.post {
            if (recyclerVew?.isAttachedToWindow == true && (mMessageAdapter?.itemCount ?: 0) > 0) {
                mLinearLayoutManager?.scrollToPositionWithOffset(0, 0)
            }
        }
    }
}
