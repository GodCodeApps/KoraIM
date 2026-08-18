package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.constant.MsgType
import org.json.JSONObject

/** Red-packet message metadata. Money movement must be implemented by the host business server. */
class RedPacketAttachment : MsgAttachment {
    var packetId: String = ""
    var amountFen: Long = 0L
    var greeting: String = DEFAULT_GREETING
    var state: Int = STATE_UNCLAIMED

    constructor()

    constructor(json: String) {
        runCatching {
            val value = JSONObject(json)
            packetId = value.optString("packetId")
            amountFen = value.optLong("amountFen")
            greeting = value.optString("greeting", DEFAULT_GREETING).ifBlank { DEFAULT_GREETING }
            state = value.optInt("state", STATE_UNCLAIMED)
        }
    }

    override fun getMsgType(): Int = MsgType.RED_PACKET

    override fun toJson(send: Boolean): String = JSONObject().apply {
        put("packetId", packetId)
        put("amountFen", amountFen)
        put("greeting", greeting)
        put("state", state)
    }.toString()

    companion object {
        const val STATE_UNCLAIMED = 0
        const val STATE_RECEIVED = 1
        const val STATE_EXPIRED = 2
        const val DEFAULT_GREETING = "恭喜发财，大吉大利"
    }
}
