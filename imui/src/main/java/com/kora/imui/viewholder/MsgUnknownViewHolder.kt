package com.kora.imui.viewholder

import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.UnknownAttachment

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2026/07/16:19:38
 * @Description:
 */
class MsgUnknownViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    private val attachment: UnknownAttachment get() = mMessage?.getAttachment() as UnknownAttachment

    override fun getLayout(): Int {
        return R.layout.im_message_item_unknow
    }

    override fun bindViewHolder(view: View, message: IMMessage) {
        view.findViewById<AppCompatTextView>(R.id.tv_content)?.text = attachment.content
    }
}