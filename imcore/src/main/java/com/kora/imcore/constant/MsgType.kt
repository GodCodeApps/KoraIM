package com.kora.imcore.constant

import androidx.annotation.IntDef

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/12/15:10:12
 * @Description:基本消息类型
 */
@IntDef(MsgType.UNDEF, MsgType.TEXT, MsgType.IMAGE, MsgType.VIDEO, MsgType.TIP)
@Retention(AnnotationRetention.SOURCE)
annotation class MsgType {
    companion object {
        const val UNDEF = -1
        const val TEXT = 1
        const val IMAGE = 2
        const val VIDEO = 3
        const val TIP = 4
    }
}