package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import org.json.JSONObject

class LocationAttachment : MsgAttachment {
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var address: String = ""

    constructor()

    constructor(json: String) {
        val jsonObj = runCatching { JSONObject(json) }.getOrNull()
        if (jsonObj != null) {
            latitude = jsonObj.optDouble("latitude", 0.0)
            longitude = jsonObj.optDouble("longitude", 0.0)
            address = jsonObj.optString("address", "")
        }
    }

    override fun toJson(send: Boolean): String {
        return JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("address", address)
        }.toString()
    }

    override fun getMsgType(): Int = TYPE_LOCATION

    companion object {
        const val TYPE_LOCATION = 1002
    }
}
