package com.kora.imcore

import com.kora.imcore.db.Message
import com.kora.imcore.event.IMEventHub
import com.kora.imcore.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object IMRuntime {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var messages: MessageRepository

    fun incoming(message: Message) {
        scope.launch { messages.upsert(message) }
        IMEventHub.emitIncoming(message)
    }

    fun updated(message: Message) {
        scope.launch { messages.upsert(message) }
        IMEventHub.emitUpdate(message)
    }
}
