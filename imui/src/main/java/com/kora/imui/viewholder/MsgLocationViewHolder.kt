package com.kora.imui.viewholder

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.LocationAttachment

class MsgLocationViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    override fun getLayout(): Int = R.layout.item_msg_location

    override fun bindViewHolder(view: View, message: IMMessage) {
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        val tvAddress = view.findViewById<TextView>(R.id.tv_address)
        val ivMap = view.findViewById<ImageView>(R.id.iv_map)

        val attachment = message.getAttachment() as? LocationAttachment ?: return
        
        tvTitle.text = attachment.title
        if (attachment.address.isNotEmpty()) {
            tvAddress.text = attachment.address
            tvAddress.visibility = View.VISIBLE
        } else {
            tvAddress.visibility = View.GONE
        }

        // Load remote snapshot if available, else local snapshot, else placeholder
        val imgUrl = if (attachment.remoteSnapshotUrl.isNotEmpty()) attachment.remoteSnapshotUrl else attachment.snapshotPath
        if (imgUrl.isNotEmpty()) {
            com.bumptech.glide.Glide.with(ivMap.context)
                .load(imgUrl)
                .centerCrop()
                .placeholder(R.drawable.im_bg_card)
                .into(ivMap)
        } else {
            ivMap.setImageResource(R.drawable.im_bg_card)
        }

        // Click handling is delegated to SessionEventListener by MsgViewHolderBase.
        // The host app decides whether this opens a map page, navigation, or another UI.
    }
}
