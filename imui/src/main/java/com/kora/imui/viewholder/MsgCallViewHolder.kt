package com.kora.imui.viewholder

import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import com.kora.imcore.ImSdkImpl
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.CallAttachment

class MsgCallViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    override fun getLayout(): Int = R.layout.im_message_item_call

    override fun bindViewHolder(view: View, message: IMMessage) {
        val attachment = message.getAttachment() as? CallAttachment ?: return
        val icon = view.findViewById<ImageView>(R.id.tv_call_icon).apply {
            setImageResource(if (attachment.callType == "video") R.drawable.ic_msg_video_call else R.drawable.ic_msg_voice_call)
            scaleX = if (isReceivedMsg()) 1f else -1f
        }
        val text = view.findViewById<TextView>(R.id.tv_call_content).apply {
            this.text = attachment.displayText(ImSdkImpl.getAccount().orEmpty())
        }
        val content = view.findViewById<LinearLayout>(R.id.call_message_content)
        content.removeAllViews()
        val gap = (8 * view.resources.displayMetrics.density).toInt()
        if (isReceivedMsg()) {
            icon.layoutParams = LinearLayout.LayoutParams(icon.layoutParams).apply { marginStart = 0; marginEnd = gap }
            text.layoutParams = LinearLayout.LayoutParams(text.layoutParams)
            content.addView(icon)
            content.addView(text)
        } else {
            text.layoutParams = LinearLayout.LayoutParams(text.layoutParams)
            icon.layoutParams = LinearLayout.LayoutParams(icon.layoutParams).apply { marginStart = gap; marginEnd = 0 }
            content.addView(text)
            content.addView(icon)
        }
    }
}
