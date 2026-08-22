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
        val tvAddress = view.findViewById<TextView>(R.id.tv_address)
        val ivMap = view.findViewById<ImageView>(R.id.iv_map)

        val attachment = message.getAttachment() as? LocationAttachment ?: return
        tvAddress.text = attachment.address

        // In a real app, you would load a static map image from Amap/Baidu using Glide here.
        // For now, we'll just set a placeholder image.
        ivMap.setImageResource(R.drawable.ic_more_location)
        ivMap.scaleType = ImageView.ScaleType.CENTER
    }
}
