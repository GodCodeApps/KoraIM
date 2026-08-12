package com.kora.imcore

import com.kora.imcore.attachment.MsgAttachment
import java.util.*

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2026/07/24:16:09
 * @Description:
 */
object ImSdkImpl {
    private var mAccount: String? = null
    private var msgAttachmentList = arrayListOf<MsgAttachment>()
    fun init() {
        val loader: ServiceLoader<MsgAttachment> = ServiceLoader.load(MsgAttachment::class.java)
        msgAttachmentList.addAll(loader.toMutableList())
    }

    fun getMsgAttachmentList(): List<MsgAttachment> = msgAttachmentList

    fun setAccount(account: String?) {
        mAccount = account
    }
    fun getAccount(): String? = mAccount

}