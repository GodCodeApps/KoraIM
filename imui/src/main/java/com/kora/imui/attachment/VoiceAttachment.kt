package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.constant.MsgType
import org.json.JSONException
import org.json.JSONObject

class VoiceAttachment : MsgAttachment {
    var path: String = ""
    var duration: Long = 0

    constructor()
    constructor(attach: String) {
        fromJson(attach)
    }

    override fun getMsgType(): Int {
        return MsgType.VOICE
    }

    override fun toJson(send: Boolean): String {
        val jsonObject = JSONObject()
        try {
            jsonObject.put("path", path)
            jsonObject.put("duration", duration)
        } catch (e: JSONException) {
        }
        return jsonObject.toString()
    }

    private fun fromJson(attach: String) {
        try {
            val jsonObject = JSONObject(attach)
            path = jsonObject.getString("path")
            duration = jsonObject.getLong("duration")
        } catch (e: JSONException) {
        }
    }
}
