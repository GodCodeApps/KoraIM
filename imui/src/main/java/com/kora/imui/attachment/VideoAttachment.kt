package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.constant.MsgType
import org.json.JSONObject

class VideoAttachment : MsgAttachment {
    var localPath: String = ""
    var remoteUrl: String = ""
    var localCoverPath: String = ""
    var remoteCoverUrl: String = ""
    var duration: Long = 0L
    var width: Int = 0
    var height: Int = 0
    var size: Long = 0L
    var mimeType: String = ""

    constructor()

    constructor(attach: String) {
        runCatching {
            val json = JSONObject(attach)
            localPath = json.optString("localPath")
            remoteUrl = json.optString("remoteUrl")
            localCoverPath = json.optString("localCoverPath")
            remoteCoverUrl = json.optString("remoteCoverUrl")
            duration = json.optLong("duration")
            width = json.optInt("width")
            height = json.optInt("height")
            size = json.optLong("size")
            mimeType = json.optString("mimeType")
        }
    }

    override fun getMsgType(): Int = MsgType.VIDEO

    override fun toJson(send: Boolean): String = JSONObject().apply {
        if (!send) put("localPath", localPath)
        put("remoteUrl", remoteUrl)
        if (!send) put("localCoverPath", localCoverPath)
        put("remoteCoverUrl", remoteCoverUrl)
        put("duration", duration)
        put("width", width)
        put("height", height)
        put("size", size)
        put("mimeType", mimeType)
    }.toString()
}
