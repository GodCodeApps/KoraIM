package com.kora.imui.listener

import com.kora.imcore.impl.IMMessage

/**
 * 会话事件监听器：
 * 支持监听头像点击/长按、消息气泡点击/长按，以及发送失败消息的重试点击。
 */
open class SessionEventListener {
    private var onAvatarClickListener: ((view: android.view.View, account: String?) -> Unit)? = null
    private var onAvatarLongClickListener: ((view: android.view.View, account: String?) -> Unit)? = null
    private var onItemClickListener: ((view: android.view.View, message: IMMessage?) -> Unit)? = null
    private var onItemLongClickListener: ((view: android.view.View, message: IMMessage?) -> Boolean)? = null
    private var onResendClickListener: ((view: android.view.View, message: IMMessage?) -> Unit)? = null
    private var onMoreOptionClickListener: ((optionName: String) -> Unit)? = null
    private var onForwardMessageListener: ((IMMessage) -> Unit)? = null

    fun onAvatarClickListener(avatarClickListener: ((view: android.view.View, account: String?) -> Unit)?) {
        this.onAvatarClickListener = avatarClickListener
    }

    fun onAvatarLongClickListener(avatarLongClickListener: ((view: android.view.View, account: String?) -> Unit)?) {
        this.onAvatarLongClickListener = avatarLongClickListener
    }

    fun onItemClickListener(itemClickListener: ((view: android.view.View, message: IMMessage?) -> Unit)?) {
        this.onItemClickListener = itemClickListener
    }

    fun onItemLongClickListener(itemLongClickListener: ((view: android.view.View, message: IMMessage?) -> Boolean)?) {
        this.onItemLongClickListener = itemLongClickListener
    }

    fun onResendClickListener(resendClickListener: ((view: android.view.View, message: IMMessage?) -> Unit)?) {
        this.onResendClickListener = resendClickListener
    }

    fun onMoreOptionClickListener(moreOptionClickListener: ((optionName: String) -> Unit)?) {
        this.onMoreOptionClickListener = moreOptionClickListener
    }
    fun onForwardMessageListener(listener: ((IMMessage) -> Unit)?) { onForwardMessageListener = listener }

    fun getAvatarClickListener(): ((view: android.view.View, account: String?) -> Unit)? = onAvatarClickListener
    fun getAvatarLongClickListener(): ((view: android.view.View, account: String?) -> Unit)? = onAvatarLongClickListener
    fun getItemClickListener(): ((view: android.view.View, message: IMMessage?) -> Unit)? = onItemClickListener
    fun getItemLongClickListener(): ((view: android.view.View, message: IMMessage?) -> Boolean)? = onItemLongClickListener
    fun getResendClickListener(): ((view: android.view.View, message: IMMessage?) -> Unit)? = onResendClickListener
    fun getMoreOptionClickListener(): ((optionName: String) -> Unit)? = onMoreOptionClickListener
    fun getForwardMessageListener(): ((IMMessage) -> Unit)? = onForwardMessageListener
}

inline fun sessionEventListener(listener: SessionEventListener.() -> Unit): SessionEventListener {
    return SessionEventListener().apply(listener)
}
