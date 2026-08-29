package com.kora.imui.viewholder

import android.view.View
import android.widget.TextView
import com.kora.imcore.ImSdkImpl
import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.TextAttachment
import com.kora.imui.quote.QuoteActionDispatcher

class MsgRecallViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    override fun getLayout(): Int = R.layout.im_message_item_recall
    override fun isMiddleItem(): Boolean = true

    override fun bindViewHolder(view: View, message: IMMessage) {
        val text = view.findViewById<TextView>(R.id.tv_recall_content)
        val edit = view.findViewById<TextView>(R.id.tv_reedit)
        val mine = message.senderId == ImSdkImpl.getAccount() || message.getMsgDirection() == MsgDirection.OUT
        text.text = if (mine) "你撤回了一条消息" else "对方撤回了一条消息"
        val original = message.getAttachment() as? TextAttachment
        edit.visibility = if (mine && !original?.content.isNullOrBlank()) View.VISIBLE else View.GONE
        edit.setOnClickListener { original?.content?.let { QuoteActionDispatcher.onReedit?.invoke(it) } }
    }
}
