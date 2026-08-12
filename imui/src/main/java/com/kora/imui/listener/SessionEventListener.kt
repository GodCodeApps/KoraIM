package com.kora.imui.listener

import com.kora.imcore.impl.IMMessage


/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2026/07/21:14:59
 * @Description:
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
