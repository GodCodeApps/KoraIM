package com.kora.imui.attachment

import com.kora.imcore.attachment.MsgAttachment
import com.kora.imcore.constant.MsgType
import org.json.JSONObject

class CallAttachment(json: String? = null) : MsgAttachment {
    var callId = ""
    var callType = "audio"
    var result = "interrupted"
    var callerId = ""
    var calleeId = ""
    var actorId = ""
    var durationSeconds = 0L
    var endedAt = 0L

    init {
        if (!json.isNullOrBlank()) runCatching {
            JSONObject(json).also {
                callId = it.optString("callId")
                callType = it.optString("callType", "audio")
                result = it.optString("result", "interrupted")
                callerId = it.optString("callerId")
                calleeId = it.optString("calleeId")
                actorId = it.optString("actorId")
                durationSeconds = it.optLong("durationSeconds")
                endedAt = it.optLong("endedAt")
            }
        }
    }

    override fun getMsgType(): Int = MsgType.CALL

    override fun toJson(send: Boolean): String = JSONObject().apply {
        put("callId", callId)
        put("callType", callType)
        put("result", result)
        put("callerId", callerId)
        put("calleeId", calleeId)
        put("actorId", actorId)
        put("durationSeconds", durationSeconds)
        put("endedAt", endedAt)
    }.toString()

    fun displayText(ownerId: String): String = when (result) {
        "completed" -> "通话时长 ${durationSeconds / 60}:${(durationSeconds % 60).toString().padStart(2, '0')}"
        "cancelled" -> if (actorId == ownerId) "已取消" else "对方已取消"
        "rejected" -> if (actorId == ownerId) "已拒绝" else "对方已拒绝"
        "missed" -> if (ownerId == callerId) "对方无应答" else "未接听"
        "busy" -> if (ownerId == callerId) "对方忙线" else "未接听"
        else -> "通话中断"
    }

    fun digest(ownerId: String): String = "[${if (callType == "video") "视频通话" else "语音通话"}] ${displayText(ownerId)}"
}
