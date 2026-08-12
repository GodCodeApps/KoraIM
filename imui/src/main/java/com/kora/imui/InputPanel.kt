package com.zchd.vsports.im.ui

import android.os.Build
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.constant.SessionType
import com.kora.imcore.impl.IMMessage
import com.kora.imui.MessageBuilder.createTextMessage
import com.kora.imui.R
import com.kora.imui.adapter.MsgListAdapter
import com.kora.imui.bean.BaseInputAction
import com.kora.imui.inputbox.ChatInputView
import com.kora.imui.inputbox.ChatInputView.OnInputListener
import com.kora.imui.module.ModuleProxy


/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Date: 2021/12/15:10:02
 * @Description:聊天底部输入区域控制
 */
@RequiresApi(Build.VERSION_CODES.N)
class InputPanel(
    var proxy: ModuleProxy?,
    var sessionId: String,
    var sessionType: Int,
    var actions: List<BaseInputAction>?,
    var rootView: View
) {
    private val chatInputView = rootView.findViewById<ChatInputView>(R.id.chat_input_view)
    private val rvMessages = rootView.findViewById<RecyclerView>(R.id.rv_messages)
    private var messageAdapter: MsgListAdapter? = null
    private lateinit var messageList: MutableList<IMMessage>
    private var onMoreActionClickListener: ((Int) -> Unit)? = null
    fun setActionClickListener(cb: (res: Int) -> Unit) {
        this.onMoreActionClickListener = cb
    }

    class Builder {
        private var mProxy: ModuleProxy? = null
        private var mActions: List<BaseInputAction>? = null
        private var mSessionId: String = ""
        private var mSessionType: Int = SessionType.None
        fun setProxy(proxy: ModuleProxy): Builder {
            this.mProxy = proxy
            return this
        }

        fun setSessionId(sessionId: String): Builder {
            this.mSessionId = sessionId
            return this
        }

        fun setSessionType(@SessionType sessionType: Int = SessionType.None): Builder {
            this.mSessionType = sessionType
            return this
        }

        fun setActions(actions: List<BaseInputAction>): Builder {
            this.mActions = actions
            return this
        }

        fun build(rootView: View): InputPanel {
            return InputPanel(mProxy, mSessionId, mSessionType, mActions, rootView)
        }
    }


    init {
        initViews()
        initViewPager()
        initListener()
    }


    private fun initViews() {
        initMessageList()
    }

    private fun initViewPager() {

    }


    private fun initMessageList() {
        val layoutManager = LinearLayoutManager(proxy?.getAppActivity())
        rvMessages.setLayoutManager(layoutManager)
        rvMessages.setAdapter(messageAdapter)
        // (可选) 添加一条初始的欢迎消息
        messageAdapter?.addItem(createTextMessage("你好！开始聊天吧。"))
    }

    private fun initListener() {
        // 设置监听器来接收来自组件的事件
        chatInputView.setOnInputListener(object : OnInputListener {
            override fun onSendMessage(message: String) {
                // 处理发送消息的逻辑，例如添加到 RecyclerView
                Toast.makeText(proxy?.getAppActivity(), "发送: " + message, Toast.LENGTH_SHORT)
                    .show()

                if (TextUtils.isEmpty(message.toString().trim { it <= ' ' })) {
                    Toast.makeText(proxy?.getAppActivity(), "不能发送空消息", Toast.LENGTH_SHORT)
                        .show()
                    return
                }
                // 创建一个新的 MessageItem
                val createTextMessage = createTextMessage(message)
                // 添加到适配器并滚动到底部
                messageAdapter?.addItem(createTextMessage)
                rvMessages.scrollToPosition(messageAdapter!!.getItemCount() - 1)

                // (可选) 模拟接收一条消息
                simulateReceiveMessage()
            }

            override fun onVoiceClick() {
                // 处理点击语音按钮的逻辑
                Toast.makeText(proxy?.getAppActivity(), "语音按钮点击", Toast.LENGTH_SHORT).show()
            }

            override fun onMoreOptionClick(optionName: String?) {
                Toast.makeText(proxy?.getAppActivity(), optionName + "按钮点击", Toast.LENGTH_SHORT)
                    .show()
            }

            override fun onEmojiClick(emojiTag: String?) {
                chatInputView.insertText(emojiTag)
            }
        })

//
//        var listener = View.OnClickListener { v ->
//            when (v?.id) {
//                R.id.tv_input_send -> {
//                    val text = edInputText?.text.toString()
//                    proxy?.sendMessage(
//                        MessageBuilder.createTextMessage(
//                            sessionId = sessionId,
//                            sessionType = sessionType,
//                            msg = text
//                        )
//                    )
//                    edInputText?.setText("")
//                }
//                R.id.ed_input_text -> {
//                    recyclerViewAction?.visibility = View.GONE
//                }
//            }
//        }
//        ivInputSend?.setOnClickListener(listener)
//        ivInputSend?.setOnClickListener(listener)
//        edInputText?.setOnClickListener(listener)
//        ivInputMore?.setOnClickListener {
//            Album.Builder()
//                .setMaxNum(9)
//                .setMode(Album.IMAGE_TYPE)
//                .setFileProvider("${proxy!!.getAppActivity().packageName}.fileprovider")
//                .build()
//                .setResultListener {
//                    it?.forEach {
//                        it.takeIf { null != it }?.let {
//                            var point =
//                                PhotoMetadataUtils.getBitmapBound(
//                                    proxy!!.getAppActivity().contentResolver,
//                                    PathUtils.getUriFromPath(it)
//                                )
//                            proxy?.sendMessage(
//                                MessageBuilder.createImageMessage(
//                                    sessionId = sessionId,
//                                    sessionType = sessionType,
//                                    localPath = it,
//                                    mWidth = point?.x ?: 0,
//                                    mHeight = point?.y ?: 0
//                                )
//                            )
//                        }
//                    }
//
//
//                }.start(proxy!!.getAppActivity())
//        }
    }

    // 模拟接收消息，用于演示
    private fun simulateReceiveMessage() {
        // 延迟 1 秒后执行
        rvMessages.postDelayed({
            val receivedMessage =
                createTextMessage("我收到了你的消息！", msgDirect = MsgDirection.IN)
            messageAdapter?.addItem(receivedMessage)
            rvMessages.scrollToPosition(messageAdapter!!.getItemCount() - 1)
        }, 1000)
    }
}