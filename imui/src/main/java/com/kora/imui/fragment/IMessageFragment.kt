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
import com.zchd.vsports.im.ui.InputPanel

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2026/07/15:09:35
 * @Description:im基类
 */
abstract class IMessageFragment : Fragment(), ModuleProxy {
    val sessionType get() = arguments?.getInt("session_type") ?: SessionType.None
    val sessionId get() = arguments?.getString("session_id") ?: ""
    var messageListPanelEx: MessageListPanelEx? = null

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
        messageListPanelEx = MessageListPanelEx(this, sessionId, view, true)
        InputPanel.Builder()
            .setProxy(this)
            .setSessionId(sessionId)
            .setSessionType(sessionType)
            .build(view, messageListPanelEx = messageListPanelEx!!)

    }

    override fun getAppActivity(): FragmentActivity {
        return requireActivity()
    }

    override fun onDestroy() {
        IMClient.clear()
        super.onDestroy()
    }

    override fun sendMessage(msg: IMMessage): Boolean {
        if (sessionId != msg.getIMSessionId()) {
            return false
        }
        IMClient.saveMessageToLocal(msg)
        messageListPanelEx?.addItemMsg(msg)
        IMClient.sendMessage(msg)
        return true
    }
}