package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.constant.MsgType
import org.json.JSONException
import org.json.JSONObject

/**
 * Copyright (C), 2020-2021, 中传互动（湖北）信息技术有限公司
 * @Author: pym
 * @Date: 2021/4/30 15:53
 * @Description:未知消息
 */
class UnknownAttachment : MsgAttachment {
    var content: String = ""
    override fun getMsgType(): Int = MsgType.UNDEF

    constructor()
    constructor(attach: String) {
        fromJson(attach)
    }

    override fun toJson(send: Boolean): String {
        val jsonObject = JSONObject()
        try {
            jsonObject.put("content", content)
        } catch (e: JSONException) {
        }
        return jsonObject.toString()
    }

    private fun fromJson(attach: String) {
        val jsonObject = JSONObject(attach)
        content = jsonObject.toString()
    }

}