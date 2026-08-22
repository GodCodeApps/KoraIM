package com.kora.imui.viewholder

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.CardAttachment
import com.kora.imui.viewholder.MsgViewHolderFactory

class MsgCardViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    override fun getLayout(): Int = R.layout.item_msg_card

    override fun bindViewHolder(view: View, message: IMMessage) {
        val card = MsgViewHolderFactory.getAttachment(message) as? CardAttachment ?: return
        
        val tvNickname = view.findViewById<TextView>(R.id.tv_card_nickname)
        val ivAvatar = view.findViewById<ImageView>(R.id.iv_card_avatar)

        tvNickname.text = card.nickname
        
        Glide.with(view.context)
            .load(card.avatar.takeIf { it.isNotBlank() })
            .placeholder(R.drawable.ic_default_avatar)
            .error(R.drawable.ic_default_avatar)
            .centerCrop()
            .into(ivAvatar)
    }
}
