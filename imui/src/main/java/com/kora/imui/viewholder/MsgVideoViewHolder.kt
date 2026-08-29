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
import java.io.File

/**
 * 视频消息气泡 ViewHolder：
 * 展示视频首帧封面与时长，点击弹窗全屏播放视频。
 */
class MsgVideoViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    private val attachment get() = mMessage?.getAttachment() as VideoAttachment

    override fun getLayout(): Int = R.layout.im_message_item_video

    override fun bindViewHolder(view: View, message: IMMessage) {
        val cover = view.findViewById<ImageView>(R.id.iv_video_cover)
        val container = view.findViewById<View>(R.id.video_container)
        val duration = view.findViewById<TextView>(R.id.tv_video_duration)
        val source = attachment.localPath
            .takeIf { it.isNotBlank() && File(it).isFile }
            ?: attachment.remoteUrl
        val coverSource = attachment.localCoverPath
            .takeIf { it.isNotBlank() && File(it).isFile }
            ?: attachment.remoteCoverUrl.ifBlank { source }
        val density = view.resources.displayMetrics.density
        val maxWidth = (240 * density).toInt()
        val maxHeight = (240 * density).toInt()
        val minHeight = (140 * density).toInt()
        val ratio = if (attachment.width > 0 && attachment.height > 0) {
            (attachment.width.toFloat() / attachment.height).coerceIn(0.6f, 1.8f)
        } else 4f / 3f
        val params = container.layoutParams
        params.width = maxWidth
        params.height = (maxWidth / ratio).toInt().coerceIn(minHeight, maxHeight)
        container.layoutParams = params
        Glide.with(cover)
            .load(coverSource)
            .placeholder(R.drawable.bg_media_placeholder)
            .error(R.drawable.media_error_placeholder)
            .centerCrop()
            .into(cover)
        duration.text = formatDuration(attachment.duration)
//        container.setOnClickListener {
//            if (source.isBlank()) return@setOnClickListener
//            val dialog = Dialog(view.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
//            val videoView = VideoView(view.context)
//            dialog.setContentView(
//                videoView,
//                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
//            )
//            videoView.setMediaController(MediaController(view.context).apply { setAnchorView(videoView) })
//            val uri = Uri.parse(source)
//            if (uri.scheme.isNullOrBlank()) videoView.setVideoPath(source) else videoView.setVideoURI(uri)
//            videoView.setOnPreparedListener { videoView.start() }
//            videoView.setOnCompletionListener { dialog.dismiss() }
//            dialog.setOnDismissListener { videoView.stopPlayback() }
//            dialog.show()
//        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
