package com.kora.imui.viewholder

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
import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.impl.IMMessage
import com.kora.imui.ImUIKitImpl
import com.kora.imui.R
import com.zchd.vsports.im.core.constant.MsgStatus

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/12/16:18:42
 * @Description:
 */
open class MsgViewHolderBase(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var mMessage: IMMessage? = null
    open fun getLayout(): Int = 0
    open fun isMiddleItem(): Boolean = false
    open fun isReceivedMsg(): Boolean = mMessage?.getMsgDirection() === MsgDirection.IN
    open fun bindViewHolder(view: View, message: IMMessage) {}
    open fun onBindViewHolder(message: IMMessage) {
        this.mMessage = message
        val parentContainer =
            itemView.findViewById<FrameLayout>(R.id.message_item_content)
        bindParentView()
        parentContainer.removeAllViews()
        val childView =
            LayoutInflater.from(itemView.context).inflate(getLayout(), parentContainer, false)
        bindViewHolder(childView, message)
        parentContainer.addView(childView)
    }

    private fun bindParentView() {
        var tvTime = itemView.findViewById<AppCompatTextView>(R.id.tv_time)
        var leftAvatar = itemView.findViewById<AppCompatImageView>(R.id.iv_left_avatar)
        var rightAvatar = itemView.findViewById<AppCompatImageView>(R.id.iv_right_avatar)
        var contentContainer = itemView.findViewById<FrameLayout>(R.id.message_item_content)
        var llBody = itemView.findViewById<LinearLayoutCompat>(R.id.ll_body)
        var flMsgStatus = itemView.findViewById<FrameLayout>(R.id.fl_msg_status)
        var progress = itemView.findViewById<ProgressBar>(R.id.progress)
        var ivMsgStatus = itemView.findViewById<AppCompatImageView>(R.id.iv_msg_status)
        tvTime?.text = mMessage?.getMsgTime().toString()
        if (isMiddleItem()) {
            leftAvatar?.visibility = View.GONE
            rightAvatar?.visibility = View.GONE
            return
        }
        if (isReceivedMsg()) {
            leftAvatar?.visibility = View.VISIBLE
            rightAvatar?.visibility = View.GONE
            flMsgStatus?.visibility = View.GONE
            contentContainer?.setBackgroundResource(R.drawable.im_msg_left_bg)
            setGravity(llBody, Gravity.LEFT)
        } else {
            rightAvatar?.visibility = View.VISIBLE
            leftAvatar?.visibility = View.GONE
            flMsgStatus?.visibility = View.VISIBLE
            contentContainer?.setBackgroundResource(R.drawable.im_msg_right_bg)
            setGravity(llBody, Gravity.RIGHT)
            setMsgStatus(progress, ivMsgStatus)
        }
        contentContainer?.setOnClickListener {
            ImUIKitImpl.getSessionListener()?.getItemClickListener()?.invoke(
                mMessage
            )
        }
        leftAvatar?.setOnClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarClickListener()?.invoke(
                mMessage?.getFromAccount()
            )
        }
        leftAvatar?.setOnLongClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarLongClickListener()?.invoke(
                mMessage?.getFromAccount()
            )
            true
        }
        rightAvatar?.setOnClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarClickListener()?.invoke(
                mMessage?.getFromAccount()
            )
        }
        rightAvatar?.setOnLongClickListener {
            ImUIKitImpl.getSessionListener()?.getAvatarLongClickListener()?.invoke(
                mMessage?.getFromAccount()
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
        viewGroup.layoutParams = params
    }
}