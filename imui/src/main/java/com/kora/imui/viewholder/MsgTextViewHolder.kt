package com.kora.imui.viewholder

import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.TextAttachment
import com.kora.imui.inputbox.EmojiDisplayUtils

/** 文本与 Emoji 表情消息气泡 ViewHolder */
class MsgTextViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    private val attachment: TextAttachment get() = mMessage?.getAttachment() as TextAttachment
    override fun getLayout(): Int {
        return R.layout.im_message_item_text
    }

    override fun bindViewHolder(view: View, message: IMMessage) {
        val textView = view.findViewById<AppCompatTextView>(R.id.tv_content)
        EmojiDisplayUtils.display(view.context,textView,attachment.content)
    }
}
