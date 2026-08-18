package com.kora.imui.viewholder

import android.app.AlertDialog
import android.view.View
import android.widget.TextView
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.RedPacketAttachment
import java.util.Locale
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MsgRedPacketViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    private val attachment: RedPacketAttachment
        get() = mMessage?.getAttachment() as RedPacketAttachment

    override fun getLayout(): Int = R.layout.im_message_item_red_packet

    override fun bindViewHolder(view: View, message: IMMessage) {
        val packet = attachment
        view.findViewById<TextView>(R.id.tv_red_packet_greeting).text = packet.greeting
        view.findViewById<TextView>(R.id.tv_red_packet_state).text = when (packet.state) {
            RedPacketAttachment.STATE_RECEIVED -> "红包已领取"
            RedPacketAttachment.STATE_EXPIRED -> "红包已过期"
            else -> "微信红包"
        }
        
        val maskView = view.findViewById<View>(R.id.v_red_packet_mask)
        if (packet.state == RedPacketAttachment.STATE_UNCLAIMED) {
            maskView?.visibility = View.GONE
            view.alpha = 1f
            view.setOnClickListener {
                if (message.getMsgDirection() == com.kora.imcore.constant.MsgDirection.OUT) {
                    android.widget.Toast.makeText(view.context, "自己发的红包自己不能拆", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                val dialog = com.kora.imui.widget.RedPacketOpenDialog(
                    view.context,
                    packet,
                    "好友"
                ) {
                    // Update state to received and refresh the UI
                    packet.state = RedPacketAttachment.STATE_RECEIVED
                    message.getMessage().attachment = packet.toJson(message.getMsgDirection() == com.kora.imcore.constant.MsgDirection.OUT)
                    bindViewHolder(view, message)
                    
                    view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                        com.kora.imcore.IMClient.saveMessage(message)
                        
                        // Send remote tip message to the sender so they know who opened it
                        val remoteTipMsg = com.kora.imui.MessageBuilder.createTipMessage(
                            message.getIMSessionId(),
                            message.getIMSessionType(),
                            message.senderId, // We send back to the original sender
                            "好友领取了你的红包", // This is what the sender will see
                            com.kora.imcore.constant.MsgDirection.OUT
                        )
                        val tipAttachment = remoteTipMsg.getAttachment() as com.kora.imui.attachment.TipAttachment
                        tipAttachment.redPacketMsgId = message.getMsgId()
                        remoteTipMsg.getMessage().attachment = tipAttachment.toJson(true)
                        
                        com.kora.imcore.IMClient.sendMessage(remoteTipMsg)
                    }
                }
                dialog.show()
            }
        } else {
            maskView?.visibility = View.VISIBLE
            view.alpha = 0.65f
            view.setOnClickListener(null) // Disable click completely if already claimed/expired
        }
    }
}
