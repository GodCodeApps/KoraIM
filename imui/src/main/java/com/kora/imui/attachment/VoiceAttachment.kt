package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.constant.MsgType
import org.json.JSONException
import org.json.JSONObject

class VoiceAttachment : MsgAttachment {
    var localPath: String = ""
    var remoteUrl: String = ""
    var duration: Long = 0
    var size: Long = 0L
    var mimeType: String = ""

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
            if (!send) jsonObject.put("localPath", localPath)
            jsonObject.put("remoteUrl", remoteUrl)
            jsonObject.put("duration", duration)
            jsonObject.put("size", size)
            jsonObject.put("mimeType", mimeType)
        } catch (e: JSONException) {
        }
        return jsonObject.toString()
    }

    private fun fromJson(attach: String) {
        try {
            val jsonObject = JSONObject(attach)
            localPath = jsonObject.optString("localPath", jsonObject.optString("path"))
            remoteUrl = jsonObject.optString("remoteUrl")
            duration = jsonObject.optLong("duration")
            size = jsonObject.optLong("size")
            mimeType = jsonObject.optString("mimeType")
        } catch (e: JSONException) {
        }
    }
}
