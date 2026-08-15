package com.kora.imcore.netty

import com.google.gson.Gson

class WireEnvelopeTest {
    private val gson = Gson()

//    @Test
//    fun messageFrame_isNewlineDelimitedAndRoundTrips() {
//        val message = Message(
//            messageId = "message-1",
//            sessionType = SessionType.P2P,
//            sessionId = "session-1",
//            senderId = "sender-1",
//            receiverId = "receiver-1",
//            type = MsgType.TEXT,
//            direct = MsgDirection.OUT,
//            status = MsgStatus.SENDING,
//            time = 1L,
//            attachment = "{\"content\":\"hello\"}"
//        )
//
//        val frame = WireEnvelope.message(message).encode(gson)
//        val decoded = gson.fromJson(frame.trimEnd(), WireEnvelope::class.java)
//
//        assertTrue(frame.endsWith("\n"))
//        assertEquals(WireEnvelope.TYPE_MESSAGE, decoded.type)
//        assertEquals(message.messageId, decoded.messageId)
//        assertEquals(message.attachment, decoded.payload?.attachment)
//    }
}
