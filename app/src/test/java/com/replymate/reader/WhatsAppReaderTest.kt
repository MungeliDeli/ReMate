package com.replymate.reader

import com.replymate.Config
import com.replymate.service.SupportedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppReaderTest {
    private val w = 1080
    private val h = 2400
    private val root = DumpParser.parse(DumpParser.resource("whatsapp_chat.txt"), "com.whatsapp")

    @Test
    fun readsMessagesInOrderWithSenders() {
        val c = WhatsAppReader.readConversation(root, w, h)
        assertNotNull(c)
        c!!
        assertEquals(SupportedApp.WHATSAPP, c.app)
        assertEquals(
            listOf(
                Sender.THEM to "hey are you coming tonight?",
                Sender.ME to "which place again",
                Sender.THEM to "the usual spot at 7, bring the speaker and call 0971234567 when you're close lol",
                Sender.ME to "sharp sharp, I'll be there by 7 and I'll bring the speaker no stress at all",
                Sender.THEM to "nice 🔥",
            ),
            c.messages.map { it.sender to it.text },
        )
    }

    @Test
    fun readsContactNameFromToolbarId() {
        assertEquals("Chanda Mulenga", WhatsAppReader.readConversation(root, w, h)!!.contactName)
    }

    @Test
    fun fallbackWithoutViewIdsStillReadsMessages() {
        val stripped = stripIds(root)
        val c = WhatsAppReader.readConversation(stripped, w, h)!!
        // Same 5 messages; date chips, e2e notice, unread banner and timestamps are filtered out.
        assertEquals(5, c.messages.size)
        assertEquals(Sender.THEM, c.messages.first().sender)
        assertEquals(Sender.ME, c.messages[1].sender)
        assertEquals("Chanda Mulenga", c.contactName) // toolbar fallback
    }

    @Test
    fun keepsOnlyNewestMessagesAndTrimsLongOnes() {
        val total = Config.MAX_MESSAGES + 5
        val bubbles = (0 until total).map { i ->
            val top = 300 + i * 40
            ScreenNode(viewId = "com.whatsapp:id/message_text", text = if (i == total - 1) "x".repeat(800) else "m$i", bounds = Box(60, top, 500, top + 30))
        }
        val c = WhatsAppReader.readConversation(ScreenNode(children = bubbles, bounds = Box(0, 0, w, h)), w, h)!!
        assertEquals(Config.MAX_MESSAGES, c.messages.size)
        assertEquals("m5", c.messages.first().text) // oldest 5 dropped
        assertTrue(c.messages.last().text.length <= Config.MAX_MESSAGE_CHARS + 1)
    }

    @Test
    fun returnsNullWhenNothingReadable() {
        val empty = ScreenNode(bounds = Box(0, 0, w, h), children = listOf(ScreenNode(className = "EditText", editable = true, bounds = Box(100, 2200, 900, 2300))))
        assertNull(WhatsAppReader.readConversation(empty, w, h))
    }

    private fun stripIds(n: ScreenNode): ScreenNode = n.copy(viewId = null, children = n.children.map(::stripIds))
}
