package com.kora.imui.viewholder

import android.app.Dialog
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import com.bumptech.glide.Glide
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.VideoAttachment

class MsgVideoViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    private val attachment get() = mMessage?.getAttachment() as VideoAttachment

    override fun getLayout(): Int = R.layout.im_message_item_video

    override fun bindViewHolder(view: View, message: IMMessage) {
        val cover = view.findViewById<ImageView>(R.id.iv_video_cover)
        val duration = view.findViewById<TextView>(R.id.tv_video_duration)
        val source = attachment.remoteUrl.ifBlank { attachment.localPath }
        val coverSource = attachment.remoteCoverUrl.ifBlank {
            attachment.localCoverPath.ifBlank { source }
        }
        Glide.with(cover).load(coverSource).centerCrop().into(cover)
        duration.text = formatDuration(attachment.duration)
        view.findViewById<View>(R.id.video_container).setOnClickListener {
            if (source.isBlank()) return@setOnClickListener
            val dialog = Dialog(view.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val videoView = VideoView(view.context)
            dialog.setContentView(
                videoView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            videoView.setMediaController(MediaController(view.context).apply { setAnchorView(videoView) })
            val uri = Uri.parse(source)
            if (uri.scheme.isNullOrBlank()) videoView.setVideoPath(source) else videoView.setVideoURI(uri)
            videoView.setOnPreparedListener { videoView.start() }
            videoView.setOnCompletionListener { dialog.dismiss() }
            dialog.setOnDismissListener { videoView.stopPlayback() }
            dialog.show()
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
