package com.kora.imui

import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.kora.imcore.IMClient
import com.kora.imcore.impl.IMMessage
import com.kora.imui.adapter.MsgListAdapter

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/4/28 10:10
 * @Description:管理消息列表
 */
class MessageListPanelEx(
    var context: Fragment,
    var sessionId: String,
    var rootView: View,
    var remote: Boolean
) {
    private var recyclerVew = rootView.findViewById<RecyclerView>(R.id.rv_messages)
    private var edInputText = rootView.findViewById<AppCompatEditText>(R.id.et_message)

    private var mMessageAdapter: MsgListAdapter? = null
    private var mPage = 0
    private var mLinearLayoutManager: LinearLayoutManager? = null
    private fun createSmoothScroller(): LinearSmoothScroller {
        return object : LinearSmoothScroller(context.requireContext()) {
            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics?): Float {
                return 0.0000000000000000001f
            }
        }
    }

    init {
        mPage = 0
        mMessageAdapter = MsgListAdapter()
        recyclerVew?.setHasFixedSize(true)
        mLinearLayoutManager =
            LinearLayoutManager(recyclerVew.context, LinearLayoutManager.VERTICAL, true)
        mLinearLayoutManager?.stackFromEnd = true
        recyclerVew.layoutManager = mLinearLayoutManager
        recyclerVew.adapter = mMessageAdapter
        recyclerVew.itemAnimator?.changeDuration = 0
        loadMessageHistory()
//        refreshLayout?.setOnRefreshListener {
//            mPage++
//            loadMessageHistory()
//        }
        IMClient.queryLaseMessageBySessionId(sessionId)?.observe(context) {
            Log.e("loadMessageHistory>>>", "$it")
        }
        /**
         * 监听键盘弹起
         */
        var height = 0
        rootView.post { height = rootView.height }
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val visibleHeight: Int = r.height()
            if (height != visibleHeight) {
                scrollToBottom()
            }
        }
        var imm =
            context.context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        recyclerVew.setOnTouchListener { v, event ->
            imm.hideSoftInputFromWindow(edInputText.windowToken, 0)
            false
        }
        observerMsgChangerListener()
    }

    private fun observerMsgChangerListener() {
        IMClient.registerReceiveListener(context.requireContext()) {
            mMessageAdapter?.addItem(it)
            scrollToBottom()
        }
        IMClient.registerMessageChangeListener {
            mMessageAdapter?.notify(it)

        }
    }

    private fun loadMessageHistory() {
        IMClient.queryAllMessageListBySessionId( sessionId)?.observe(context) {
                Log.e("loadMessageHistory", it.toString())
                mMessageAdapter?.clear()
                mMessageAdapter?.addList(it)
                scrollToBottom()
            }

    }

    fun addItemMsg(message: IMMessage) {
        mMessageAdapter?.addItem(message)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        if (recyclerVew?.isAttachedToWindow == true) {
            val scroller = createSmoothScroller()
            scroller.targetPosition = 0
            mLinearLayoutManager?.startSmoothScroll(scroller)
        }
    }
}