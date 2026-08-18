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
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/16:18:42
 * @Description:
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
            return
        }
        val isMedia = mMessage?.getMsgType() == MsgType.IMAGE ||
            mMessage?.getMsgType() == MsgType.VIDEO ||
            mMessage?.getMsgType() == MsgType.RED_PACKET
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
                    customResend.invoke(message)
                } else {
                    itemView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
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

        // 1. 列表复用时，先立刻展示默认头像，防止头像错乱闪烁
        targetAvatarView?.let { imageView ->
            Glide.with(itemView.context)
                .load(defaultAvatarRes)
                .circleCrop()
                .into(imageView)
        }
        // 2. 异步/同步获取用户信息
        val boundMessageId = mMessage?.getMsgId()
        itemView.post {
            val launch = itemView.findViewTreeLifecycleOwner()
            launch?.lifecycleScope?.launch {
                val userInfo = IMClient.getUserInfo(account)
                if (mMessage?.getMsgId() != boundMessageId) return@launch
                val avatarUrl = userInfo?.avatar
                targetAvatarView?.let { imageView ->
                    if (!avatarUrl.isNullOrEmpty()) {
                        // 使用 Glide 加载网络或本地路径头像，带有占位图和错误图
                        Glide.with(itemView.context)
                            .load(avatarUrl)
                            .placeholder(defaultAvatarRes)
                            .error(defaultAvatarRes)
                            .circleCrop()
                            .into(imageView)
                    } else {
                        // 明确没有头像数据，确保展示默认头像
                        Glide.with(itemView.context)
                            .load(defaultAvatarRes)
                            .into(imageView)
                    }
                }
            }
        }

        contentContainer?.setOnClickListener {
            ImUIKitImpl.getSessionListener()?.getItemClickListener()?.invoke(
                mMessage
            )
        }
        leftAvatar?.setOnClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarClickListener()?.invoke(
                mMessage?.senderId
            )
        }
        leftAvatar?.setOnLongClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarLongClickListener()?.invoke(
                mMessage?.senderId
            )
            true
        }
        rightAvatar?.setOnClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarClickListener()?.invoke(
                mMessage?.senderId
            )
        }
        rightAvatar?.setOnLongClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarLongClickListener()?.invoke(
                mMessage?.senderId
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
}
