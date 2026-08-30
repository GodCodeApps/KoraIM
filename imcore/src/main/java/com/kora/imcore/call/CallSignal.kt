package com.kora.imcore.call

data class CallSignal(
    val callId: String,
    val action: String,
    val senderId: String = "",
    val receiverId: String,
    val callType: String = TYPE_VIDEO,
    val payload: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_AUDIO = "audio"
        const val TYPE_VIDEO = "video"
        const val INVITE = "invite"
        const val RINGING = "ringing"
        const val ACCEPT = "accept"
        const val READY = "ready"
        const val REJECT = "reject"
        const val CANCEL = "cancel"
        const val HANGUP = "hangup"
        const val BUSY = "busy"
        const val TIMEOUT = "timeout"
        const val OFFER = "offer"
        const val ANSWER = "answer"
        const val ICE = "ice"
    }
}
