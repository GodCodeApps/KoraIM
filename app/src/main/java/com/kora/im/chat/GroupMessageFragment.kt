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
        }
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