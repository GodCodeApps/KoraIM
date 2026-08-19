package com.kora.imui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.kora.imcore.constant.SessionType
import com.kora.imcore.db.Conversation
import com.kora.imui.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ConversationListItem(
    val conversation: Conversation,
    val title: String,
    val avatar: String
)

internal class ConversationListAdapter(
    private val context: Context,
    private val onClick: (ConversationListItem) -> Unit
) : BaseAdapter() {
    private var items = emptyList<ConversationListItem>()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    fun submit(newItems: List<ConversationListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = getItem(position).conversation.id

    override fun getView(position: Int, oldView: View?, parent: ViewGroup): View {
        val view = oldView ?: LayoutInflater.from(context).inflate(R.layout.im_conversation_item, parent, false)
        val item = getItem(position)
        val conversation = item.conversation
        view.findViewById<TextView>(R.id.im_conversation_title).text = item.title
//        view.findViewById<TextView>(R.id.im_conversation_type).text = when (conversation.sessionType) {
//            SessionType.P2P -> "单聊"
//            SessionType.GROUP -> "群聊"
//            else -> "会话"
//        }
        val tvPreview = view.findViewById<TextView>(R.id.im_conversation_preview)
        if (conversation.lastMessageStatus == com.kora.imcore.constant.MsgStatus.FAIL) {
            val failPrefix = "[发送失败] "
            val fullText = failPrefix + conversation.lastMessagePreview
            val spannable = android.text.SpannableString(fullText)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#E53935")),
                0,
                failPrefix.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            tvPreview.text = spannable
        } else {
            tvPreview.text = conversation.lastMessagePreview
        }
        view.findViewById<TextView>(R.id.im_conversation_time).text =
            conversation.lastMessageTime.takeIf { it > 0 }?.let { timeFormat.format(Date(it)) }.orEmpty()
        view.findViewById<TextView>(R.id.im_conversation_unread).apply {
            visibility = if (conversation.unreadCount > 0) View.VISIBLE else View.GONE
            text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString()
        }
        Glide.with(view)
            .load(item.avatar.takeIf(String::isNotBlank))
            .placeholder(R.drawable.ic_default_avatar)
            .error(R.drawable.ic_default_avatar)
            .circleCrop()
            .into(view.findViewById<ImageView>(R.id.im_conversation_avatar))
        view.setOnClickListener { onClick(item) }
        return view
    }
}

