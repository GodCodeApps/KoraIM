package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import org.json.JSONObject

class CardAttachment : MsgAttachment {
    var accountId: String = ""
    var nickname: String = ""
    var avatar: String = ""

    constructor()

    constructor(json: String) {
        runCatching {
            val value = JSONObject(json)
            accountId = value.optString("accountId")
            nickname = value.optString("nickname")
            avatar = value.optString("avatar")
        }
    }

    override fun getMsgType(): Int = TYPE_CARD

    override fun toJson(send: Boolean): String = JSONObject().apply {
        put("accountId", accountId)
        put("nickname", nickname)
        put("avatar", avatar)
    }.toString()

    companion object {
        const val TYPE_CARD = 1001
    }
}
