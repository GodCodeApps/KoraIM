package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.constant.MsgType
import org.json.JSONObject

class FileAttachment : MsgAttachment {
    var localPath = ""
    var remoteUrl = ""
    var name = ""
    var size = 0L
    var mimeType = "application/octet-stream"

    constructor()
    constructor(json: String) {
        runCatching {
            val obj = JSONObject(json)
            localPath = obj.optString("localPath")
            remoteUrl = obj.optString("remoteUrl")
            name = obj.optString("name")
            size = obj.optLong("size")
            mimeType = obj.optString("mimeType", mimeType)
        }
    }

    override fun getMsgType() = TYPE_FILE

    override fun toJson(send: Boolean) = JSONObject().apply {
        if (!send) put("localPath", localPath)
        put("remoteUrl", remoteUrl)
        put("name", name)
        put("size", size)
        put("mimeType", mimeType)
    }.toString()

    companion object {
        const val TYPE_FILE = 7
    }
}
