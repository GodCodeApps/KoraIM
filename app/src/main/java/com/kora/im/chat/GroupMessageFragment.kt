package com.kora.im.chat

import com.kora.imui.fragment.IMessageFragment

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/15:10:24
 * @Description:
 */
class GroupMessageFragment : IMessageFragment() {
    override fun onMoreOptionClick(optionName: String) {
        super.onMoreOptionClick(optionName)
        if (optionName == "个人名片") {
            showCardSelectorDialog()
        } else if (optionName == "位置") {
            simulateLocationSelection()
        } else if (optionName == "红包") {
            showRedPacketDialog()
        }
    }

    private fun showRedPacketDialog() {
        val context = context ?: return
        val content = android.view.LayoutInflater.from(context)
            .inflate(com.kora.im.R.layout.dialog_send_red_packet, null, false)
        val amountInput = content.findViewById<android.widget.EditText>(com.kora.im.R.id.et_red_packet_amount)
        val greetingInput = content.findViewById<android.widget.EditText>(com.kora.im.R.id.et_red_packet_greeting)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("发红包")
            .setView(content)
            .setNegativeButton("取消", null)
            .setPositiveButton("塞钱进红包", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).apply {
                setTextColor(android.graphics.Color.parseColor("#D94E3F"))
                setOnClickListener {
                    val amount = runCatching {
                        java.math.BigDecimal(amountInput.text.toString().trim())
                    }.getOrNull()
                    if (amount == null || amount <= java.math.BigDecimal.ZERO) {
                        amountInput.error = "请输入正确的金额"
                        return@setOnClickListener
                    }
                    if (amount > java.math.BigDecimal("200")) {
                        amountInput.error = "单个红包不能超过200元"
                        return@setOnClickListener
                    }
                    if (amount.scale() > 2) {
                        amountInput.error = "金额最多保留两位小数"
                        return@setOnClickListener
                    }
                    val amountFen = amount.movePointRight(2).longValueExact()
                    val greeting = greetingInput.text.toString().trim()
                        .ifBlank { com.kora.imui.attachment.RedPacketAttachment.DEFAULT_GREETING }
                    val attachment = com.kora.imui.attachment.RedPacketAttachment().apply {
                        packetId = java.util.UUID.randomUUID().toString()
                        this.amountFen = amountFen
                        this.greeting = greeting
                    }
                    val message = com.kora.imui.MessageBuilder.createRedPacketMessage(
                        sessionId = sessionId,
                        sessionType = sessionType,
                        receiverId = if (!peerId.isNullOrEmpty()) peerId else sessionId,
                        attachment = attachment
                    )
                    if (sendMessage(message)) {
                        dialog.dismiss()
                    } else {
                        android.widget.Toast.makeText(context, "红包发送失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun simulateLocationSelection() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("模拟地图选点")
            .setMessage("假设你打开了高德/百度地图，并选择了一个位置：\n成都市武侯区天府软件园")
            .setPositiveButton("发送该位置") { _, _ ->
                val msg = com.kora.imui.MessageBuilder.createLocationMessage(
                    sessionId = sessionId,
                    sessionType = sessionType,
                    receiverId = if (!peerId.isNullOrEmpty()) peerId else sessionId,
                    latitude = 30.5432,
                    longitude = 104.0623,
                    address = "成都市武侯区天府软件园"
                )
                sendMessage(msg)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCardSelectorDialog() {
        val accounts = com.kora.im.DemoUsers.accounts
        val displayNames = accounts.map { account ->
            val info = com.kora.im.DemoUsers.demoUser(account)
            if (info != null) "${info.nickname} ($account)" else account
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("选择要发送的名片")
            .setItems(displayNames) { _, which ->
                val selectedAccount = accounts[which]
                val info = com.kora.im.DemoUsers.demoUser(selectedAccount)
                if (info != null) {
                    val msg = com.kora.imui.MessageBuilder.createCardMessage(
                        sessionId = sessionId,
                        sessionType = sessionType,
                        receiverId = if (!peerId.isNullOrEmpty()) peerId else sessionId,
                        accountId = info.account,
                        nickname = info.nickname,
                        avatar = info.avatarUrl
                    )
                    sendMessage(msg)
                }
            }
            .show()
    }
}