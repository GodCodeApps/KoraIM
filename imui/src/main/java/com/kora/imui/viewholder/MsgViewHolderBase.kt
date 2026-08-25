package com.kora.imui.viewholder

import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.kora.imcore.IMClient
import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.constant.MsgType
import com.kora.imcore.impl.IMMessage
import com.kora.imui.ImUIKitImpl
import com.kora.imui.IMMediaMessageSender
import com.kora.imui.R
import com.kora.imui.utils.TimeFormatUtils
import com.kora.imcore.constant.MsgStatus
import kotlinx.coroutines.launch

/**
 * 消息气泡基类 ViewHolder：
 * 负责通用框架逻辑（发送/接收左右对齐、头像/昵称展示、时间分割线计算、发送中/失败重试状态展示）。
 */
open class MsgViewHolderBase(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var mMessage: IMMessage? = null
    open fun getLayout(): Int = 0
    open fun isMiddleItem(): Boolean = false
    open fun isReceivedMsg(): Boolean = mMessage?.getMsgDirection() == MsgDirection.IN
    open fun bindViewHolder(view: View, message: IMMessage) {}
    open fun onBindViewHolder(message: IMMessage, prevMessage: IMMessage? = null) {
        this.mMessage = message
        val parentContainer =
            itemView.findViewById<FrameLayout>(R.id.message_item_content)
        bindParentView(prevMessage)
        parentContainer.removeAllViews()
        val childView =
            LayoutInflater.from(itemView.context).inflate(getLayout(), parentContainer, false)
        bindViewHolder(childView, message)
        parentContainer.addView(childView)
    }

    private fun bindParentView(prevMessage: IMMessage?) {
        var tvTime = itemView.findViewById<AppCompatTextView>(R.id.tv_time)
        var leftAvatar = itemView.findViewById<AppCompatImageView>(R.id.iv_left_avatar)
        var rightAvatar = itemView.findViewById<AppCompatImageView>(R.id.iv_right_avatar)
        var contentContainer = itemView.findViewById<FrameLayout>(R.id.message_item_content)
        var llBody = itemView.findViewById<LinearLayoutCompat>(R.id.ll_body)
        var flMsgStatus = itemView.findViewById<FrameLayout>(R.id.fl_msg_status)
        var progress = itemView.findViewById<ProgressBar>(R.id.progress)
        var ivMsgStatus = itemView.findViewById<AppCompatImageView>(R.id.iv_msg_status)

        // 仿微信时间显示逻辑 (间隔大于5分钟显示)
        val currTime = mMessage?.getMsgTime() ?: 0L
        if (prevMessage == null) {
            tvTime?.visibility = View.VISIBLE
            tvTime?.text = TimeFormatUtils.formatWeChatTime(currTime)
        } else {
            val prevTime = prevMessage.getMsgTime()
//            if (currTime - prevTime > 5 * 60 * 1000) {
            tvTime?.visibility = View.VISIBLE
            tvTime?.text = TimeFormatUtils.formatWeChatTime(currTime)
//            } else {
//                tvTime?.visibility = View.GONE
//            }
        }

        if (isMiddleItem()) {
            leftAvatar?.visibility = View.GONE
            rightAvatar?.visibility = View.GONE
            flMsgStatus?.visibility = View.GONE
            contentContainer?.setBackgroundResource(0)
            contentContainer?.backgroundTintList = null
            contentContainer?.setPadding(0, 0, 0, 0)
            if (llBody != null) {
                val params = llBody.layoutParams as FrameLayout.LayoutParams
                params.gravity = Gravity.CENTER_HORIZONTAL
                params.marginStart = 0
                params.marginEnd = 0
                llBody.layoutParams = params
            }
            return
        }
        val isMedia = mMessage?.getMsgType() == MsgType.IMAGE ||
            mMessage?.getMsgType() == MsgType.VIDEO ||
            mMessage?.getMsgType() == com.kora.imui.attachment.FileAttachment.TYPE_FILE ||
            mMessage?.getMsgType() == MsgType.RED_PACKET ||
            mMessage?.getMsgType() == com.kora.imui.attachment.CardAttachment.TYPE_CARD ||
            mMessage?.getMsgType() == com.kora.imui.attachment.LocationAttachment.TYPE_LOCATION
        if (isReceivedMsg()) {
            leftAvatar?.visibility = View.VISIBLE
            rightAvatar?.visibility = View.GONE
            flMsgStatus?.visibility = View.GONE
            if (isMedia) {
                contentContainer?.setBackgroundResource(0)
            } else {
                contentContainer?.setBackgroundResource(R.drawable.im_msg_left_bg)
            }
            contentContainer?.backgroundTintList = null // 重置接收方可能存在的 tint
            setGravity(llBody, Gravity.LEFT)
        } else {
            leftAvatar?.visibility = View.GONE
            rightAvatar?.visibility = View.VISIBLE
            flMsgStatus?.visibility = View.VISIBLE
            if (isMedia) {
                contentContainer?.setBackgroundResource(0)
                contentContainer?.backgroundTintList = null
            } else {
                contentContainer?.setBackgroundResource(R.drawable.im_msg_right_bg)
                contentContainer?.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#95EC69")) // 微信绿
            }
            setGravity(llBody, Gravity.RIGHT)
            setMsgStatus(progress, ivMsgStatus)
            ivMsgStatus.setOnClickListener {
                val message = mMessage ?: return@setOnClickListener
                if (message.getMsgStatus() != MsgStatus.FAIL) return@setOnClickListener
                val customResend = ImUIKitImpl.getSessionListener()?.getResendClickListener()
                if (customResend != null) {
                    customResend.invoke(it, message)
                } else {
                    launchWhenAttached(itemView) {
                        if (IMMediaMessageSender.isMedia(message.getMessage())) {
                            IMMediaMessageSender.enqueue(message.getMessage())
                        } else {
                            message.setMsgStatus(MsgStatus.SENDING)
                            IMClient.sendMessage(message)
                        }
                    }
                }
            }
        }

        // 统一处理头像加载与默认头像兜底
        val account = mMessage?.senderId
        val defaultAvatarRes = R.drawable.ic_default_avatar
        val targetAvatarView = if (isReceivedMsg()) leftAvatar else rightAvatar

        targetAvatarView?.let { imageView ->
            val boundMessageId = mMessage?.getMsgId()
            // 1. 尝试通过 Provider 同步获取（最快，没有延迟，完美配合 Glide 内存缓存）
            val syncInfo = account?.let { IMClient.userInfoProvider?.getUserInfo(it) }
            if (syncInfo != null && !syncInfo.avatar.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(syncInfo.avatar)
                    .placeholder(defaultAvatarRes)
                    .error(defaultAvatarRes)
                    .circleCrop()
                    .into(imageView)
            } else if (syncInfo != null) {
                Glide.with(itemView.context)
                    .load(defaultAvatarRes)
                    .circleCrop()
                    .into(imageView)
            } else {
                // 2. 如果 Provider 也没有，先展示占位图，防止滑动复用时显示错乱头像
                Glide.with(itemView.context)
                    .load(defaultAvatarRes)
                    .circleCrop()
                    .into(imageView)

                // 3. 异步去 DB 或网络拉取
                launchWhenAttached(itemView) {
                    val userInfo = IMClient.getUserInfo(account)
                    if (mMessage?.getMsgId() != boundMessageId) return@launchWhenAttached
                    val avatarUrl = userInfo?.avatar
                    if (!avatarUrl.isNullOrEmpty()) {
                        Glide.with(itemView.context)
                            .load(avatarUrl)
                            .placeholder(defaultAvatarRes)
                            .error(defaultAvatarRes)
                            .circleCrop()
                            .into(imageView)
                    } else {
                        Glide.with(itemView.context)
                            .load(defaultAvatarRes)
                            .circleCrop()
                            .into(imageView)
                    }
                }
            }
        }

        contentContainer?.setOnClickListener {
            ImUIKitImpl.getSessionListener()?.getItemClickListener()?.invoke(
                it, mMessage
            )
        }
        contentContainer?.setOnLongClickListener {
            val consumed = ImUIKitImpl.getSessionListener()?.getItemLongClickListener()?.invoke(it, mMessage) ?: false
            if (consumed) return@setOnLongClickListener true
            showDefaultMessageActionDialog(itemView.context, mMessage)
            true
        }
        leftAvatar?.setOnClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarClickListener()?.invoke(
                it, mMessage?.senderId
            )
        }
        leftAvatar?.setOnLongClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarLongClickListener()?.invoke(
                it, mMessage?.senderId
            )
            true
        }
        rightAvatar?.setOnClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarClickListener()?.invoke(
                it, mMessage?.senderId
            )
        }
        rightAvatar?.setOnLongClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarLongClickListener()?.invoke(
                it, mMessage?.senderId
            )
            true
        }
    }

    private fun setMsgStatus(progressBar: ProgressBar, imageView: AppCompatImageView) {
        when (mMessage?.getMsgStatus()) {
            MsgStatus.SENDING -> {
                progressBar.visibility = View.VISIBLE
                imageView.visibility = View.GONE
            }

            MsgStatus.SUCCESS -> {
                progressBar.visibility = View.GONE
                imageView.visibility = View.GONE
            }

            MsgStatus.FAIL -> {
                progressBar.visibility = View.GONE
                imageView.visibility = View.VISIBLE
            }
        }
    }

    private fun setGravity(viewGroup: ViewGroup, gravity: Int) {
        val params = viewGroup.layoutParams as FrameLayout.LayoutParams
        params.gravity = gravity
        if (gravity == Gravity.LEFT) {
            params.marginEnd = 50
            params.marginStart = 0
        } else {
            params.marginEnd = 0
            params.marginStart = 50

        }
        viewGroup.layoutParams = params
    }

    protected open fun showDefaultMessageActionDialog(context: android.content.Context, message: IMMessage?) {
        val msg = message ?: return
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        val attachment = msg.getAttachment()
        val textContent = when (attachment) {
            is com.kora.imui.attachment.TextAttachment -> attachment.content
            else -> if (msg.getMsgType() == MsgType.TEXT) msg.getMessage().attachment else null
        }

        if (!textContent.isNullOrEmpty()) {
            options.add("复制")
            actions.add {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("message", textContent)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        options.add("删除")
        actions.add {
            android.app.AlertDialog.Builder(context)
                .setTitle("删除消息")
                .setMessage("确定删除这条消息吗？")
                .setPositiveButton("删除") { _, _ ->
                    launchWhenAttached(itemView) {
                        IMClient.deleteMessage(msg.getMsgId())
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        android.app.AlertDialog.Builder(context)
            .setItems(options.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.invoke()
            }
            .show()
    }

    protected fun launchWhenAttached(view: View, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        val owner = view.findViewTreeLifecycleOwner()
        if (owner != null) {
            owner.lifecycleScope.launch(block = block)
        } else {
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    v.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(block = block)
                }
                override fun onViewDetachedFromWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                }
            })
        }
    }
}
