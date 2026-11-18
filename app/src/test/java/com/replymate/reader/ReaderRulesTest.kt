package com.replymate.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRulesTest {
    private val w = 1080

    @Test
    fun senderByHorizontalCentre() {
        assertEquals(Sender.ME, ReaderRules.senderFor(Box(700, 0, 1000, 10), w))
        assertEquals(Sender.THEM, ReaderRules.senderFor(Box(60, 0, 400, 10), w))
        // Long incoming message stretching to 85% of the width is still THEM.
        assertEquals(Sender.THEM, ReaderRules.senderFor(Box(60, 0, 918, 10), w))
        // Long outgoing message starting at 17% is still ME.
        assertEquals(Sender.ME, ReaderRules.senderFor(Box(180, 0, 1010, 10), w))
        assertEquals(Sender.UNKNOWN, ReaderRules.senderFor(Box(0, 0, 0, 0), w))
    }

    @Test
    fun centredChipsAreDetected() {
        assertTrue(ReaderRules.isCenteredChip(Box(450, 0, 630, 10), w)) // "TODAY"
        assertFalse(ReaderRules.isCenteredChip(Box(60, 0, 300, 10), w)) // short incoming
        assertFalse(ReaderRules.isCenteredChip(Box(780, 0, 1000, 10), w)) // short outgoing
        assertFalse(ReaderRules.isCenteredChip(Box(60, 0, 880, 10), w)) // long incoming
    }

    @Test
    fun textFiltersSkipNonMessages() {
        listOf(
            "TODAY", "Yesterday", "Monday", "18 November 2026", "November 18, 2026", "Nov 18",
            "Wed, 18 Nov", "18/11/2026", "18:05", "9:41 PM", "9:41 p.m.", "Today 9:41 PM",
            "Yesterday at 9:41 PM", "3 UNREAD MESSAGES", "1 unread message", "Seen", "Delivered",
            "Messages and calls are end-to-end encrypted. Only people in this chat…",
            "Missed voice call", "This message was deleted", "Active now", "online", "last seen today at 18:00",
        ).forEach { assertTrue("should skip: $it", TextFilters.isNoise(it)) }
    }

    @Test
    fun textFiltersKeepRealMessages() {
        listOf(
            "hey", "ok", "Today was mad", "see you monday?", "may I come?", "at 7:30 then",
            "seen that movie yet?", "18 people came lol", "sent it 👍",
        ).forEach { assertFalse("should keep: $it", TextFilters.isNoise(it)) }
    }
}
