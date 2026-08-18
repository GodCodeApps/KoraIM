package com.kora.imui.viewholder

import android.app.AlertDialog
import android.view.View
import android.widget.TextView
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.RedPacketAttachment
import java.util.Locale

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
        view.alpha = if (packet.state == RedPacketAttachment.STATE_UNCLAIMED) 1f else 0.65f
        view.setOnClickListener {
            val amount = String.format(Locale.CHINA, "%.2f", packet.amountFen / 100.0)
            AlertDialog.Builder(view.context)
                .setTitle(packet.greeting)
                .setMessage("¥ $amount\n\n当前为红包消息展示，未接入真实支付和领取接口。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }
}
