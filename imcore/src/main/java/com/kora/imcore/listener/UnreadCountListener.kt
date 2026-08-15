package com.kora.imcore.listener

/** Java-friendly callback for the total unread count of the current IM account. */
fun interface UnreadCountListener {
    fun onUnreadCountChanged(count: Int)
}

class UnreadCountSubscription internal constructor(
    private val cancelAction: () -> Unit
) {
    fun cancel() = cancelAction()
}
