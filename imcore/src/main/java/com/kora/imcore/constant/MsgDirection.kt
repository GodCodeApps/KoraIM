package com.kora.imcore.constant

import androidx.annotation.IntDef

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
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