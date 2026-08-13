package com.kora.imui.viewholder

import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.attachment.ImageAttachment

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/16:18:42
 * @Description:
 */
class MsgImageViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    private val attachment: ImageAttachment get() = mMessage?.getAttachment() as ImageAttachment
    private var maxImageSize = 640
    override fun getLayout(): Int = R.layout.im_message_item_image
    override fun bindViewHolder(view: View, message: IMMessage) {
        val imageView = view.findViewById<AppCompatImageView>(R.id.iv_image)
        Glide.with(imageView)
            .load(attachment.path)
            .into(imageView)
        val ratio = attachment.width.toFloat() / attachment.height.toFloat()
        var mWidth = if (maxImageSize > attachment.width) attachment.width else maxImageSize
        var mHeight = (mWidth / ratio).toInt()
        if (mHeight > maxImageSize) {
            val ratio = maxImageSize * 1.0f / mHeight
            mWidth = (mWidth * ratio).toInt()
            mHeight = maxImageSize
        } else {
            mWidth == mWidth
            mHeight = mHeight
        }
        val layoutParams = imageView.layoutParams
        layoutParams.width = mWidth
        layoutParams.height = mHeight
        Glide.with(imageView)
            .load(attachment.path).override(mWidth, mHeight)
            .into(imageView)
    }

}
