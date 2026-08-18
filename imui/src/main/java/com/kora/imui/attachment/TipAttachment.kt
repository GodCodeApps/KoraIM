package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.constant.MsgType
import org.json.JSONObject

class TipAttachment(jsonStr: String? = null) : MsgAttachment {
    var content: String = ""
    var redPacketMsgId: String = ""

    init {
        jsonStr?.let { fromJson(it) }
    }

    override fun getMsgType(): Int = MsgType.TIP

    override fun toJson(isSend: Boolean): String {
        val jsonObject = JSONObject()
        try {
            jsonObject.put("content", content)
            jsonObject.put("redPacketMsgId", redPacketMsgId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return jsonObject.toString()
    }

    fun fromJson(jsonStr: String) {
        try {
            val jsonObject = JSONObject(jsonStr)
            content = jsonObject.optString("content", "")
            redPacketMsgId = jsonObject.optString("redPacketMsgId", "")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
