package com.kora.imcore.attachment

import com.kora.imcore.constant.MsgType
import java.io.Serializable

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/4/30 15:51
 * @Description:
 */
interface MsgAttachment : Serializable {
    fun toJson(send: Boolean): String
    fun getMsgType(): Int = MsgType.UNDEF
}