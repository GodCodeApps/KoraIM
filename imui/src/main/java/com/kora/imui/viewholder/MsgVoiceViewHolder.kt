package com.kora.imui.viewholder

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.VoiceAttachment

class MsgVoiceViewHolder(itemView: View) : MsgViewHolderBase(itemView) {

    private val attachment: VoiceAttachment get() = mMessage?.getAttachment() as VoiceAttachment

    override fun getLayout(): Int = R.layout.im_message_item_voice

    override fun bindViewHolder(view: View, message: IMMessage) {
        val llVoiceContainer = view.findViewById<LinearLayout>(R.id.ll_voice_container)
        val ivVoiceWave = view.findViewById<ImageView>(R.id.iv_voice_wave)
        val tvDuration = view.findViewById<TextView>(R.id.tv_duration)
        
        val voiceAttachment = attachment
        val seconds = voiceAttachment.duration / 1000
        tvDuration.text = "${seconds}\""
        
        // Adjust layout direction based on sender
        llVoiceContainer.removeAllViews()
        if (isReceivedMsg()) {
            // Received: Wave on the left, duration on the right
            ivVoiceWave.rotation = 0f // Pointing right
            llVoiceContainer.addView(ivVoiceWave)
            llVoiceContainer.addView(tvDuration)
        } else {
            // Sent: Duration on the left, wave on the right
            ivVoiceWave.rotation = 180f // Pointing left
            llVoiceContainer.addView(tvDuration)
            llVoiceContainer.addView(ivVoiceWave)
        }
        
        // Adjust bubble width based on duration (1-60s)
        val minWidth = 60 // dp
        val maxWidth = 200 // dp
        val width = minWidth + (seconds * (maxWidth - minWidth) / 60)
        val params = llVoiceContainer.layoutParams
        params.width = (width * itemView.context.resources.displayMetrics.density).toInt()
        llVoiceContainer.layoutParams = params
        
        // Add click listener for playing audio
        llVoiceContainer.setOnClickListener {
            // Start animation
            ivVoiceWave.setImageResource(R.drawable.anim_voice_play)
            val animationDrawable = ivVoiceWave.drawable as android.graphics.drawable.AnimationDrawable
            animationDrawable.start()

            com.kora.imui.utils.AudioPlayHelper.playAudio(voiceAttachment.path) {
                // Completion callback
                animationDrawable.stop()
                ivVoiceWave.setImageResource(R.drawable.ic_chat_voice_3)
            }
        }
    }
}
