package com.kora.imcore

import com.kora.imcore.attachment.MsgAttachment
import java.util.*

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/24:16:09
 * @Description:
 */
object ImSdkImpl {
    private var mAccount: String? = null
    private var msgAttachmentList = arrayListOf<MsgAttachment>()
    fun init() {
        msgAttachmentList.clear()
        val loader: ServiceLoader<MsgAttachment> = ServiceLoader.load(MsgAttachment::class.java)
        msgAttachmentList.addAll(loader.toMutableList())
    }

    fun getMsgAttachmentList(): List<MsgAttachment> = msgAttachmentList

    fun setAccount(account: String?) {
        mAccount = account
    }
    fun getAccount(): String? = mAccount

}
