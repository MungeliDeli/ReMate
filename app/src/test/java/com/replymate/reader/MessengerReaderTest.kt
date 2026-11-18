package com.replymate.reader

import com.replymate.service.SupportedApp
import org.junit.Assert.assertEquals
import org.junit.Test

class MessengerReaderTest {
    private val root = DumpParser.parse(DumpParser.resource("messenger_chat.txt"), "com.facebook.orca")

    @Test
    fun readsMessagesSkippingButtonsSeenAndTimestamps() {
        val c = MessengerReader.readConversation(root, 1080, 2400)!!
        assertEquals(SupportedApp.MESSENGER, c.app)
        assertEquals(
            listOf(
                Sender.THEM to "hi! did you finish the assignment?",
                Sender.ME to "almost done, sending it tonight",
                Sender.THEM to "ok cool thanks 🙏",
                Sender.ME to "no problem", // from a content description (no text child)
            ),
            c.messages.map { it.sender to it.text },
        )
    }

    @Test
    fun contactNameIsFirstToolbarText() {
        assertEquals("Mwila Banda", MessengerReader.readConversation(root, 1080, 2400)!!.contactName)
    }
}
