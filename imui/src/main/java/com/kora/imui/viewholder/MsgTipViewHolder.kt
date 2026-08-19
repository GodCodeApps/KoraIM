package com.kora.imui.viewholder

import android.view.View
import android.widget.TextView
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.TipAttachment

import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.kora.imui.attachment.RedPacketAttachment
import com.kora.imcore.constant.MsgDirection

/**
 * 系统居中提示消息气泡 ViewHolder（如“你领取了对方的红包”）。
 */
class MsgTipViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    override fun getLayout(): Int = R.layout.im_message_item_tip

    override fun isMiddleItem(): Boolean = true

    override fun bindViewHolder(view: View, message: IMMessage) {
        val attachment = mMessage?.getAttachment() as? TipAttachment
        val tipView = view.findViewById<TextView>(R.id.tv_tip_content)
        tipView.text = attachment?.content ?: ""

        // Mock syncing the red packet state
        val redPacketMsgId = attachment?.redPacketMsgId
        if (!redPacketMsgId.isNullOrEmpty()) {
            if (message.getMsgDirection() == MsgDirection.OUT) {
                tipView.text = "你领取了好友的红包"
            } else {
                tipView.text = "好友领取了你的红包"
            }
            
        // The database update for the red packet state is now handled globally in IMessageFragment 
        // to ensure it updates even if the tip message is not on screen.
        }
    }
}
