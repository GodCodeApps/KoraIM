package com.kora.imui.viewholder

import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.ImageAttachment
import java.io.File

/**
 * 图片消息气泡 ViewHolder：
 * 根据图片真实宽高比动态计算展示尺寸，支持本地/网络图片平滑加载。
 */
class MsgImageViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    private val attachment: ImageAttachment get() = mMessage?.getAttachment() as ImageAttachment
    override fun getLayout(): Int = R.layout.im_message_item_image
    override fun bindViewHolder(view: View, message: IMMessage) {
        val imageView = view.findViewById<AppCompatImageView>(R.id.iv_image)
        val density = imageView.resources.displayMetrics.density
        val maxSize = (240 * density).toInt()
        val minSize = (120 * density).toInt()
        val sourceWidth = attachment.width.takeIf { it > 0 } ?: 4
        val sourceHeight = attachment.height.takeIf { it > 0 } ?: 3
        val ratio = (sourceWidth.toFloat() / sourceHeight).coerceIn(0.5f, 2f)
        var mWidth = maxSize
        var mHeight = (mWidth / ratio).toInt()
        if (mHeight > maxSize) {
            mHeight = maxSize
            mWidth = (mHeight * ratio).toInt()
        }
        mWidth = mWidth.coerceAtLeast(minSize)
        mHeight = mHeight.coerceAtLeast(minSize)
        val layoutParams = imageView.layoutParams
        layoutParams.width = mWidth
        layoutParams.height = mHeight
        imageView.layoutParams = layoutParams
        val localPath = attachment.localPath
        val source = localPath.takeIf { it.isNotBlank() && File(it).isFile }
            ?: attachment.remoteUrl
        Glide.with(imageView)
            .load(source)
            .placeholder(R.drawable.bg_media_placeholder)
            .error(R.drawable.media_error_placeholder)
            .override(mWidth, mHeight)
            .into(imageView)
    }

}
