package com.kora.imui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kora.imcore.impl.IMMessage
import com.kora.imui.R
import com.kora.imui.viewholder.MsgViewHolderBase
import com.kora.imui.viewholder.MsgViewHolderFactory
import kotlin.collections.get

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/20:09:52
 * @Description:
 */
class MsgListAdapter : RecyclerView.Adapter<MsgViewHolderBase>() {
    private var mMsgList = arrayListOf<IMMessage>()
    private var holderViewType = mutableMapOf<Class<out MsgViewHolderBase>, Int>()

    init {
        val holders: List<Class<out MsgViewHolderBase>> = MsgViewHolderFactory.getAllViewHolders()
        holders.forEachIndexed { index, holder ->
            holderViewType[holder] = index
        }
    }

    fun addItem(message: IMMessage) {
        mMsgList.add(0, message)
       notifyItemInserted(0)
//        if (0 != mMsgList?.size) {
//            notifyItemRangeChanged(0, mMsgList.size - 0)
//        }else{
//
//        }
//        notifyDataSetChanged()
    }

    fun clear() {
        mMsgList.clear()
        notifyDataSetChanged()
    }

    fun addList(list: List<IMMessage>) {
        mMsgList.addAll(list)
        notifyDataSetChanged()
    }

    fun notify(message: IMMessage) {
        mMsgList.forEachIndexed { index, imMessage ->
            if (imMessage.getMsgId() == message.getMsgId()) {
                imMessage.setMsgStatus(message.getMsgStatus())
                notifyItemChanged(index)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val viewHolderByType = MsgViewHolderFactory.getViewHolderByType(mMsgList[position])
        val type = holderViewType[viewHolderByType] ?: 0
        return type
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MsgViewHolderBase {
        val inflater =
            LayoutInflater.from(parent.context).inflate(R.layout.im_base_item, parent, false)
        val newInstance = holderViewType.keys.toList()[viewType]
        val constructor = newInstance.getConstructor(View::class.java)
        return constructor.newInstance(inflater)
    }

    override fun onBindViewHolder(
        holder: MsgViewHolderBase,
        position: Int
    ) {
        // 因为按 reverseLayout = true 的习惯，index 0 是最新消息，所以时间上的上一条(更老的消息)在 index + 1
        val prevMessage = if (position + 1 < mMsgList.size) mMsgList[position + 1] else null
        holder.onBindViewHolder(mMsgList[position], prevMessage)
    }

    override fun getItemCount(): Int {
        return mMsgList.size
    }
}
