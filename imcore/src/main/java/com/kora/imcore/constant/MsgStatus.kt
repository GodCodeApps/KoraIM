package com.zchd.vsports.im.core.constant

import androidx.annotation.IntDef

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2026/07/16:18:14
 * @Description:消息状态
 */
@IntDef(
    MsgStatus.DRAFT,
    MsgStatus.SENDING,
    MsgStatus.SUCCESS,
    MsgStatus.FAIL,
    MsgStatus.READ,
    MsgStatus.UNREAD
)
@Retention(AnnotationRetention.SOURCE)
annotation class MsgStatus {
    companion object {
        const val DRAFT = -1
        const val SENDING = 0
        const val SUCCESS = 1
        const val FAIL = 2
        const val READ = 3
        const val UNREAD = 4
    }
}