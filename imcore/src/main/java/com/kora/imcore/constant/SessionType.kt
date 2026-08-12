package com.kora.imcore.constant

import androidx.annotation.IntDef

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2026/07/15:10:02
 * @Description:会话类型
 */
@IntDef(
    SessionType.None,
    SessionType.P2P,
    SessionType.GROUP,
    SessionType.CHAT_ROOM,
    SessionType.CUSTOMER
)
@Retention(AnnotationRetention.SOURCE)
annotation class SessionType {
    companion object {
        const val None = 0
        const val P2P = 1
        const val GROUP = 2
        const val CHAT_ROOM = 3
        const val CUSTOMER = 4
    }
}