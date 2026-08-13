package com.kora.imui.viewholder

import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.TextAttachment
import com.kora.imui.inputbox.EmojiDisplayUtils

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/16:18:42
 * @Description:
 */
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
