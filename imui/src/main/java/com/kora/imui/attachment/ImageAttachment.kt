package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.constant.MsgType
import org.json.JSONException
import org.json.JSONObject

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/4/30 15:54
 * @Description:
 */
class ImageAttachment : MsgAttachment {
    var path: String = ""
    var width: Int = 0
    var height: Int = 0

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
            jsonObject.put("path", path)
            jsonObject.put("width", width)
            jsonObject.put("height", height)
        } catch (e: JSONException) {
        }
        return jsonObject.toString()
    }

    private fun fromJson(attach: String) {
        try {
            val jsonObject = JSONObject(attach)
            path = jsonObject.getString("path")
            width = jsonObject.getInt("width")
            height = jsonObject.getInt("height")
        } catch (e: JSONException) {
        }
    }
}