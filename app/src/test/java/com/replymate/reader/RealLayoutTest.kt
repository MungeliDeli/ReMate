package com.replymate.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures copied from real node dumps shared from the owner's phone
 * (layout and positions kept; names and text replaced).
 */
class RealLayoutTest {

    @Test
    fun whatsapp_readsAllTenMessagesWithTicksDecidingMe() {
        val root = DumpParser.parse(DumpParser.resource("whatsapp_pixel6_2026-11.txt"), "com.whatsapp")
        val c = WhatsAppReader.readConversation(root, 1080, 2400)!!
        assertEquals("Alice", c.contactName)
        assertEquals(
            listOf(
                Sender.THEM to "stuck in traffic, give me 10",
                Sender.ME to "I was going to say hurry 😂 but there's nothing you can do",
                Sender.THEM to "No wait for me 😭😭",
                Sender.ME to "I'm here and waiting, it's up to the driver now", // quoted text not included
                Sender.THEM to "Not yet there?",
                Sender.ME to "Not yet",
                // Looks centred because the time sits to its right; used to be dropped.
                Sender.ME to "I'll call when she arrives",
                Sender.THEM to "Alright",
                Sender.THEM to "Made it",
                Sender.ME to "She's here",
            ),
            c.messages.map { it.sender to it.text },
        )
    }

    @Test
    fun messenger_stripsNameAndHintAndSkipsQuotesHeadersAndReceipts() {
        val root = DumpParser.parse(DumpParser.resource("messenger_pixel6_2026-11.txt"), "com.facebook.orca")
        val c = MessengerReader.readConversation(root, 1080, 2400)!!
        assertEquals("Gina Test Banda", c.contactName)
        assertEquals(
            listOf(
                Sender.ME to "Since first year, we wrote Java from scratch 😂 calculators and to-do lists",
                Sender.ME to "Back then it was more fun honestly",
                Sender.THEM to "Writing code on paper in exams 😭",
                Sender.ME to "Looking back that was diabolical 💀😂",
                Sender.THEM to "No for real!",
                Sender.ME to "Good morning, nice weather today",
            ),
            c.messages.map { it.sender to it.text },
        )
    }

    @Test
    fun messenger_parseTagged_usesContactFirstName() {
        val b = Box(100, 0, 500, 10)
        val them = MessengerReader.parseTagged(
            ReaderRules.Candidate("Gina, hello there, double tap to see sent/receive date and time, double tap and hold to react on message", b),
            "Gina Test Banda",
        )
        assertEquals(Sender.THEM, them.sender)
        assertEquals("hello there", them.text)

        val me = MessengerReader.parseTagged(
            ReaderRules.Candidate("Sam, ok, double tap to see sent/receive date and time", b),
            "Gina Test Banda",
        )
        assertEquals(Sender.ME, me.sender)
        assertEquals("ok", me.text)

        val unknown = MessengerReader.parseTagged(
            ReaderRules.Candidate("Sam, ok, double tap to see sent/receive date and time", b),
            null,
        )
        assertEquals(null, unknown.sender) // position decides
    }

    @Test
    fun newNoiseFilters() {
        listOf(
            "Delivered 12 hours ago", "Seen by Gina Test Banda", "Sent 5 minutes ago",
            "You replied to Gina", "Gina replied to you", "Gina replied to herself",
            "Messages and calls are secured with end-to-end encryption. Only people in this chat can read",
        ).forEach { assertTrue("should skip: $it", TextFilters.isNoise(it)) }
        listOf("she replied to me lol", "read by tomorrow pls", "sent it 👍", "delivered the parcel already")
            .forEach { assertFalse("should keep: $it", TextFilters.isNoise(it)) }
    }
}
