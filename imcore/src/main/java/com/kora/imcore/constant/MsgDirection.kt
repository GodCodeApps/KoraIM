package com.kora.imcore.constant

import androidx.annotation.IntDef

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/15:10:32
 * @Description:消息发送方向
 */
@IntDef(MsgDirection.OUT, MsgDirection.IN)
@Retention(AnnotationRetention.SOURCE)
annotation class MsgDirection {
    companion object {
        const val OUT = 0
        const val IN = 1
    }
}
