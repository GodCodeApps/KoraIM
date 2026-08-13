package com.kora.imcore.attachment

import com.kora.imcore.constant.MsgType
import java.io.Serializable

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2021/4/30 15:51
 * @Description:
 */
interface MsgAttachment : Serializable {
    fun toJson(send: Boolean): String
    fun getMsgType(): Int = MsgType.UNDEF
}
