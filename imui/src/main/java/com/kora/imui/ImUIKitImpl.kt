package com.kora.imui

import com.kora.imui.listener.SessionEventListener
import com.kora.imui.provider.IMMediaMessageProvider
import com.kora.imcore.IMClient
import com.kora.imui.provider.ConversationDigestFormatter

/**
 * IM UI 组件库全局配置入口（单例）。
 * 用于设置全局点击事件监听器与多媒体上传提供器。
 */
object ImUIKitImpl {
    init {
        IMClient.conversationDigestProvider = ConversationDigestFormatter
    }
    private var sessionEventListener: SessionEventListener? = null
    private var mediaMessageProvider: IMMediaMessageProvider? = null

    /** 获取全局会话事件监听器 */
    fun getSessionListener(): SessionEventListener? = sessionEventListener

    /** 设置会话事件监听器（头像点击、气泡点击、失败重发等） */
    fun setSessionEventListener(listener: SessionEventListener?) {
        sessionEventListener = listener
    }

    /** 设置多媒体上传提供器（图片、视频、语音上传实现） */
    fun setMediaMessageProvider(provider: IMMediaMessageProvider?) {
        mediaMessageProvider = provider
    }

    /** 获取多媒体上传提供器 */
    fun getMediaMessageProvider(): IMMediaMessageProvider? = mediaMessageProvider
}

