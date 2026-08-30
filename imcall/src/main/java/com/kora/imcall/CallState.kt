package com.kora.imcall

enum class CallPhase { IDLE, OUTGOING, INCOMING, CONNECTING, CONNECTED, ENDED }

data class CallSession(
    val callId: String,
    val peerId: String,
    val callType: String,
    val outgoing: Boolean,
    val phase: CallPhase,
    val reason: String = ""
)
