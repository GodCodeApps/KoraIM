package com.kora.imcore.event

import com.kora.imcore.db.Message
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal object IMEventHub {
    private val _incomingMessages = MutableSharedFlow<Message>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _messageUpdates = MutableSharedFlow<Message>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    val incomingMessages = _incomingMessages.asSharedFlow()
    val messageUpdates = _messageUpdates.asSharedFlow()
    val connectionState = _connectionState.asStateFlow()

    fun emitIncoming(message: Message) { _incomingMessages.tryEmit(message) }
    fun emitUpdate(message: Message) { _messageUpdates.tryEmit(message) }
    fun setConnectionState(state: ConnectionState) { _connectionState.value = state }
}
