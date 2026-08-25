package com.kora.imui.viewholder

import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.kora.imui.R
import com.kora.imui.attachment.FileAttachment
import com.kora.imcore.impl.IMMessage

class MsgFileViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    private val attachment: FileAttachment get() = mMessage?.getAttachment() as FileAttachment

    override fun getLayout() = R.layout.im_message_item_file

    override fun bindViewHolder(view: View, message: IMMessage) {
        view.findViewById<AppCompatTextView>(R.id.tv_file_name)?.text = attachment.name
        view.findViewById<AppCompatTextView>(R.id.tv_file_size)?.text = formatSize(attachment.size)
    }

    private fun formatSize(size: Long): String = when {
        size >= 1024 * 1024 -> "%.1f MB".format(size / 1024f / 1024f)
        size >= 1024 -> "%.1f KB".format(size / 1024f)
        else -> "$size B"
    }
}
