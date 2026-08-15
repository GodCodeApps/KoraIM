package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.constant.MsgType
import org.json.JSONException
import org.json.JSONObject

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2021/4/30 15:54
 * @Description:
 */
class ImageAttachment : MsgAttachment {
    var localPath: String = ""
    var remoteUrl: String = ""
    var width: Int = 0
    var height: Int = 0
    var size: Long = 0L
    var mimeType: String = ""

    constructor()
    constructor(attach: String) {
        fromJson(attach)
    }

    override fun getMsgType(): Int {
        return MsgType.IMAGE
    }

    override fun toJson(send: Boolean): String {
        val jsonObject = JSONObject()
        try {
            if (!send) jsonObject.put("localPath", localPath)
            jsonObject.put("remoteUrl", remoteUrl)
            jsonObject.put("width", width)
            jsonObject.put("height", height)
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
            width = jsonObject.optInt("width")
            height = jsonObject.optInt("height")
            size = jsonObject.optLong("size")
            mimeType = jsonObject.optString("mimeType")
        } catch (e: JSONException) {
        }
    }
}
