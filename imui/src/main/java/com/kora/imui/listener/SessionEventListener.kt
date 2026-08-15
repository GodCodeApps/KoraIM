package com.kora.imui.listener

import com.kora.imcore.impl.IMMessage


/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/21:14:59
 * @Description:
 */
open class SessionEventListener {
    //聊天头像点击
    private var onAvatarClickListener: ((account: String?) -> Unit)? = null
    //聊天头像长按
    private var onAvatarLongClickListener: ((account: String?) -> Unit)? = null
    //聊天消息点击
    private var onItemClickListener: ((message: IMMessage?) -> Unit)? = null
    //聊天消息失败重发点击
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
