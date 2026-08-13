package com.kora.imcore.netty

import com.google.gson.Gson
import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.constant.MsgStatus
import com.kora.imcore.constant.MsgType
import com.kora.imcore.constant.SessionType
import com.kora.imcore.db.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireEnvelopeTest {
    private val gson = Gson()

    @Test
    fun messageFrame_isNewlineDelimitedAndRoundTrips() {
        val message = Message(
            messageId = "message-1",
            sessionType = SessionType.P2P,
            sessionId = "session-1",
            type = MsgType.TEXT,
            direct = MsgDirection.OUT,
            status = MsgStatus.SENDING,
            time = 1L,
            attachment = "{\"content\":\"hello\"}"
        )

        val frame = WireEnvelope.message(message).encode(gson)
        val decoded = gson.fromJson(frame.trimEnd(), WireEnvelope::class.java)

        assertTrue(frame.endsWith("\n"))
        assertEquals(WireEnvelope.TYPE_MESSAGE, decoded.type)
        assertEquals(message.messageId, decoded.messageId)
        assertEquals(message.attachment, decoded.payload?.attachment)
    }
}
