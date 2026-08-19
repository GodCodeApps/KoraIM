package com.kora.imui.viewholder

import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.UnknownAttachment

/** 未知/不支持消息类型的兜底气泡 ViewHolder */
class MsgUnknownViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    private val attachment: UnknownAttachment get() = mMessage?.getAttachment() as UnknownAttachment

    override fun getLayout(): Int {
        return R.layout.im_message_item_unknow
    }

    override fun bindViewHolder(view: View, message: IMMessage) {
        view.findViewById<AppCompatTextView>(R.id.tv_content)?.text = attachment.content
    }
}
