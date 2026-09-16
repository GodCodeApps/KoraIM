package com.kora.imui.viewholder

import android.view.View
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.kora.imcore.impl.IMMessage
import com.kora.imui.ImUIKitImpl
import com.kora.imui.R
import com.kora.imui.VoiceTranscriptionManager
import com.kora.imui.VoiceTranscriptionStore
import com.kora.imui.attachment.VoiceAttachment
import java.io.File

/**
 * 语音消息气泡 ViewHolder：
 * 负责时长计算、按时长比例拉伸气泡宽度，以及播放/停止动画切换。
 */
class MsgVoiceViewHolder(itemView: View) : MsgViewHolderBase(itemView) {

    private val attachment: VoiceAttachment get() = mMessage?.getAttachment() as VoiceAttachment

    override fun getLayout(): Int = R.layout.im_message_item_voice

    override fun bindViewHolder(view: View, message: IMMessage) {
        val voiceBubble = view.findViewById<FrameLayout>(R.id.voice_bubble)
        val llVoiceContainer = view.findViewById<LinearLayout>(R.id.ll_voice_container)
        val ivVoiceWave = view.findViewById<ImageView>(R.id.iv_voice_wave)
        val tvDuration = view.findViewById<TextView>(R.id.tv_duration)
        val tvTranscription = view.findViewById<TextView>(R.id.tv_voice_transcription)

        (view as? LinearLayout)?.gravity = if (isReceivedMsg()) {
            Gravity.START
        } else {
            Gravity.END
        }
        applyBubbleBackground(voiceBubble)
        applyBubbleBackground(tvTranscription)
        
        val voiceAttachment = attachment
        val cachedTranscription = VoiceTranscriptionStore
            .get(view.context, message.getMsgId())
        tvTranscription.text = cachedTranscription
        tvTranscription.visibility = if (cachedTranscription.isBlank()) View.GONE else View.VISIBLE
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

            val source = voiceAttachment.localPath
                .takeIf { it.isNotBlank() && File(it).isFile }
                ?: voiceAttachment.remoteUrl
            com.kora.imui.utils.AudioPlayHelper.playAudio(source) {
                // Completion callback
                animationDrawable.stop()
                ivVoiceWave.setImageResource(R.drawable.ic_chat_voice_3)
            }
        }
        val longClickListener = View.OnLongClickListener { sourceView ->
            val message = mMessage
                ?: return@OnLongClickListener false

            val consumed = ImUIKitImpl.getSessionListener()
                ?.getItemLongClickListener()
                ?.invoke(sourceView, message)
                ?: false

            if (!consumed) {
                showDefaultMessageActionDialog(
                    itemView.context,
                    message
                )
            }

            true
        }
        llVoiceContainer.setOnLongClickListener(longClickListener)
        // 识别结果显示在气泡下方时，长按结果区域也应打开同一个菜单。
        view.setOnLongClickListener(longClickListener)
    }

    override fun onVoiceTranscriptionRequested(
        context: android.content.Context,
        message: IMMessage,
        force: Boolean,
    ) {
        val messageId = message.getMsgId()
        VoiceTranscriptionManager.transcribe(
            context = context,
            message = message,
            force = force,
            onStarted = {
                updateTranscriptionView(messageId, "转文字中…")
            },
            onResult = { text ->
                updateTranscriptionView(messageId, text)
            },
            onError = { error ->
                android.widget.Toast.makeText(
                    context,
                    "转文字失败：${error.message ?: "未知错误"}",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
            onFinished = {},
        )
    }

    override fun onVoiceTranscriptionCancelled(
        context: android.content.Context,
        message: IMMessage,
    ) {
        VoiceTranscriptionStore.remove(context, message.getMsgId())
        updateTranscriptionView(message.getMsgId(), "")
    }

    private fun applyBubbleBackground(view: View) {
        if (isReceivedMsg()) {
            view.setBackgroundResource(R.drawable.im_msg_left_bg)
            view.backgroundTintList = null
        } else {
            view.setBackgroundResource(R.drawable.im_msg_right_bg)
            view.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#95EC69"),
            )
        }
    }

    private fun updateTranscriptionView(messageId: String, text: String) {
        itemView.post {
            if (mMessage?.getMsgId() != messageId) return@post
            val textView = itemView.findViewById<TextView>(R.id.tv_voice_transcription)
                ?: return@post
            textView.text = text
            textView.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
    }
}
