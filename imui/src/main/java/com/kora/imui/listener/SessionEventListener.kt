package com.kora.imui.listener

import com.kora.imcore.impl.IMMessage


/**
 * 会话事件监听器：
 * 支持监听头像点击/长按、消息气泡点击，以及发送失败消息的重试点击。
 */
open class SessionEventListener {
    private var onAvatarClickListener: ((account: String?) -> Unit)? = null
    private var onAvatarLongClickListener: ((account: String?) -> Unit)? = null
    private var onItemClickListener: ((message: IMMessage?) -> Unit)? = null
    private var onResendClickListener: ((message: IMMessage?) -> Unit)? = null
    fun onAvatarClickListener(avatarClickListener: ((account: String?) -> Unit)?) {
        this.onAvatarClickListener = avatarClickListener
    }

    fun onAvatarLongClickListener(avatarLongClickListener: ((account: String?) -> Unit)?) {
        this.onAvatarLongClickListener = avatarLongClickListener
    }

    fun onItemClickListener(itemClickListener: ((message: IMMessage?) -> Unit)?) {
        this.onItemClickListener = itemClickListener
    }
    
    fun onResendClickListener(resendClickListener: ((message: IMMessage?) -> Unit)?) {
        this.onResendClickListener = resendClickListener
    }

    fun getAvatarClickListener(): ((account: String?) -> Unit)? = onAvatarClickListener
    fun getAvatarLongClickListener(): ((account: String?) -> Unit)? = onAvatarLongClickListener
    fun getItemClickListener(): ((message: IMMessage?) -> Unit)? = onItemClickListener
    fun getResendClickListener(): ((message: IMMessage?) -> Unit)? = onResendClickListener

}

inline fun sessionEventListener(listener: SessionEventListener.() -> Unit): SessionEventListener {
    return SessionEventListener().apply(listener)
}
