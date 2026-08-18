package com.kora.imcore.constant

import androidx.annotation.IntDef

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/15:10:12
 * @Description:基本消息类型
 */
@IntDef(MsgType.UNDEF, MsgType.TEXT, MsgType.IMAGE, MsgType.VIDEO, MsgType.TIP, MsgType.VOICE, MsgType.RED_PACKET)
@Retention(AnnotationRetention.SOURCE)
annotation class MsgType {
    companion object {
        const val UNDEF = -1
        const val TEXT = 1
        const val IMAGE = 2
        const val VIDEO = 3
        const val TIP = 4
        const val VOICE = 5
        const val RED_PACKET = 6
    }
}
