package com.replymate.ai

import com.replymate.data.AppSettings
import com.replymate.data.Persona
import com.replymate.reader.ChatMessage
import com.replymate.reader.Conversation
import com.replymate.reader.Sender
import com.replymate.service.SupportedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyOptionsTest {
    @Test
    fun moodsCapAtTwoDroppingOldest() {
        val o = ReplyOptions().toggle(Mood.LIGHT).toggle(Mood.FLIRTY).toggle(Mood.ROMANTIC)
        assertEquals(listOf(Mood.FLIRTY, Mood.ROMANTIC), o.moods)
        assertEquals(listOf(Mood.ROMANTIC), o.toggle(Mood.FLIRTY).moods)
    }

    @Test
    fun lengthAndVibeToggleOff() {
        val o = ReplyOptions().toggle(ReplyLength.SHORT).toggle(Vibe.PLAYFUL)
        assertEquals(ReplyLength.SHORT, o.length)
        assertNull(o.toggle(ReplyLength.SHORT).length)
        assertEquals(ReplyLength.LONG, o.toggle(ReplyLength.LONG).length)
        assertNull(o.toggle(Vibe.PLAYFUL).vibe)
    }

    @Test
    fun summary() {
        assertEquals("Auto", ReplyOptions().summary())
        assertEquals(
            "Flirty + Light · Short · Playful",
            ReplyOptions(listOf(Mood.FLIRTY, Mood.LIGHT), ReplyLength.SHORT, Vibe.PLAYFUL).summary(),
        )
        assertEquals("Long", ReplyOptions(length = ReplyLength.LONG).summary())
    }
}

class PromptOptionsTest {
    private val settings = AppSettings(displayName = "Sam", personas = listOf(Persona(examples = "ok\nlol")))
    private val chat = Conversation(SupportedApp.WHATSAPP, null, listOf(ChatMessage(Sender.THEM, "hey you")))

    @Test
    fun autoKeepsDefaultTonesAndLengthRule() {
        val sys = PromptBuilder.build(settings, chat, ReplyOptions()).systemInstruction
        assertTrue(sys.contains("""these tones: "casual", "warm", "short""""))
        assertTrue(sys.contains("Match Sam's typical message length. Never write more than 3 sentences."))
    }

    @Test
    fun chosenStyleReplacesTonesAndLength() {
        val o = ReplyOptions(listOf(Mood.FLIRTY), ReplyLength.SHORT, Vibe.PLAYFUL)
        val sys = PromptBuilder.build(settings, chat, o).systemInstruction
        assertFalse(sys.contains(""""casual", "warm", "short""""))
        assertTrue(sys.contains("  - Mood: ${Mood.FLIRTY.instruction}"))
        assertTrue(sys.contains("  - Vibe: ${Vibe.PLAYFUL.instruction}"))
        assertTrue(sys.contains("- Length: ${ReplyLength.SHORT.instruction}."))
        assertFalse(sys.contains("Never write more than 3 sentences"))
        assertTrue(sys.contains("Stay in Sam's voice"))
        assertTrue(sys.contains("lean towards kindness"))
        // Persona sections are unchanged.
        assertTrue(sys.contains("REAL EXAMPLES OF Sam'S MESSAGES"))
    }

    @Test
    fun twoMoodsAreCombined() {
        val o = ReplyOptions(listOf(Mood.LIGHT, Mood.FLIRTY))
        val sys = PromptBuilder.build(settings, chat, o).systemInstruction
        assertTrue(sys.contains("  - Mood: ${Mood.LIGHT.instruction} and ${Mood.FLIRTY.instruction}"))
        // No length picked: default length rule stays.
        assertTrue(sys.contains("Never write more than 3 sentences"))
    }
}
