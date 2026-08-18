package com.kora.imui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.kora.imcore.IMClient
import com.kora.imcore.constant.SessionType
import com.kora.imcore.impl.IMMessage
import com.kora.imui.MessageListPanelEx
import com.kora.imui.R
import com.kora.imui.module.ModuleProxy
import com.kora.imui.InputPanel
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/15:09:35
 * @Description:im基类
 */
abstract class IMessageFragment : Fragment(), ModuleProxy {
    val sessionType get() = arguments?.getInt("session_type") ?: SessionType.None
    private var currentSessionId = ""
    val sessionId get() = currentSessionId
    val peerId get() = arguments?.getString("peer_id") ?: ""
    var messageListPanelEx: MessageListPanelEx? = null
    private var inputPanel: InputPanel? = null

    /**
     * 自定义底部输入框样式
     */

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_im_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentSessionId = arguments?.getString("session_id").orEmpty()
        
        // 绑定导航栏
        view.findViewById<ImageView>(R.id.iv_back)?.setOnClickListener {
            activity?.onBackPressed()
        }
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        tvTitle?.text = peerId.ifBlank { sessionId }
        
        // 尝试从 Provider 获取真实的用户名
        viewLifecycleOwner.lifecycleScope.launch {
            val userInfo = IMClient.getUserInfo(peerId.ifBlank { sessionId })
            if (userInfo != null && !userInfo.nickname.isNullOrEmpty()) {
                tvTitle?.text = userInfo.nickname
            }
        }
        
        messageListPanelEx = MessageListPanelEx(this, sessionId, peerId, view, true)
        inputPanel = InputPanel.Builder()
            .setProxy(this)
            .setSessionId(sessionId)
            .setPeerId(peerId)
            .setSessionType(sessionType)
            .build(this,view, messageListPanelEx = messageListPanelEx!!)

        // Clear existing unread messages no matter whether this page was opened from
        // the conversation list, a notification, or a user profile.
        viewLifecycleOwner.lifecycleScope.launch {
            if (currentSessionId.isBlank() && peerId.isNotBlank()) {
                IMClient.getP2PConversation(peerId)?.let { conversation ->
                    currentSessionId = conversation.sessionId
                    inputPanel?.updateSessionId(currentSessionId)
                }
            }
            if (currentSessionId.isNotBlank()) {
                IMClient.markConversationRead(currentSessionId)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                IMClient.messageUpdates.collect { message ->
                    if (currentSessionId.isBlank() && message.sessionId.isNotBlank() &&
                        message.getIMSessionType() == SessionType.P2P &&
                        (message.senderId == peerId || message.receiverId == peerId)
                    ) {
                        currentSessionId = message.sessionId
                        inputPanel?.updateSessionId(currentSessionId)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                IMClient.incomingMessages.collect { message ->
                    val belongsHere = message.sessionId == currentSessionId ||
                        (currentSessionId.isBlank() && message.getIMSessionType() == SessionType.P2P && message.senderId == peerId)
                    if (belongsHere) IMClient.markConversationRead(message.sessionId)
                    
                    if (message.getMsgType() == com.kora.imcore.constant.MsgType.TIP) {
                        val attachment = message.getAttachment() as? com.kora.imui.attachment.TipAttachment
                        val rpId = attachment?.redPacketMsgId
                        if (!rpId.isNullOrEmpty()) {
                            val rpMsg = IMClient.getMessageById(rpId)
                            if (rpMsg != null) {
                                val rpAtt = rpMsg.getAttachment() as? com.kora.imui.attachment.RedPacketAttachment
                                if (rpAtt != null && rpAtt.state != com.kora.imui.attachment.RedPacketAttachment.STATE_RECEIVED) {
                                    rpAtt.state = com.kora.imui.attachment.RedPacketAttachment.STATE_RECEIVED
                                    rpMsg.attachment = rpAtt.toJson(rpMsg.getMsgDirection() == com.kora.imcore.constant.MsgDirection.OUT)
                                    IMClient.saveMessage(rpMsg)
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    override fun getAppActivity(): FragmentActivity {
        return requireActivity()
    }

    override fun sendMessage(msg: IMMessage): Boolean {
        if (sessionId != msg.getIMSessionId()) {
            return false
        }
        messageListPanelEx?.addItemMsg(msg)
        viewLifecycleOwner.lifecycleScope.launch { IMClient.sendMessage(msg) }
        return true
    }
}
