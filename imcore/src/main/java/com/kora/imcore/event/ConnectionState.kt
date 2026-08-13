package com.kora.imcore.event

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val host: String, val port: Int) : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}
