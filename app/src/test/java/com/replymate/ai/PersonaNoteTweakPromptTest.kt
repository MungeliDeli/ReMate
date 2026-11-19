package com.replymate.ai

import com.replymate.data.AppSettings
import com.replymate.data.Persona
import com.replymate.reader.ChatMessage
import com.replymate.reader.Conversation
import com.replymate.reader.Sender
import com.replymate.service.SupportedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaNoteTweakPromptTest {
    private val calm = Persona(id = "calm", label = "Calm Closer", writingStyle = "short, lowercase", examples = "noted 😏")
    private val playful = Persona(id = "play", label = "Playful Planner", writingStyle = "playful, some slang", examples = "iwe 😂")
    private val settings = AppSettings(displayName = "Sam", personas = listOf(calm, playful), defaultPersonaId = "calm")
    private val chat = Conversation(SupportedApp.WHATSAPP, "Grace Banda", listOf(ChatMessage(Sender.THEM, "hey stranger")))

    @Test
    fun usesTheChosenPersonaOnly() {
        val sys = PromptBuilder.build(settings, chat, persona = playful).systemInstruction
        assertTrue(sys.contains("HOW Sam WRITES:\nplayful, some slang"))
        assertTrue(sys.contains("- iwe 😂"))
        assertFalse(sys.contains("short, lowercase"))
        // Default persona when none given.
        assertTrue(PromptBuilder.build(settings, chat).systemInstruction.contains("short, lowercase"))
    }

    @Test
    fun contactNoteIsAddedAndRedacted() {
        val sys = PromptBuilder.build(settings, chat, contactNote = "my crush, her number is 0971234567").systemInstruction
        assertTrue(sys.contains("Sam'S NOTE ABOUT THEM"))
        assertTrue(sys.contains("my crush, her number is [PHONE]"))
        assertFalse(sys.contains("0971234567"))
        assertFalse(PromptBuilder.build(settings, chat).systemInstruction.contains("NOTE ABOUT"))
    }

    @Test
    fun tweakRewritesTheDraft() {
        val p = PromptBuilder.build(settings, chat, tweak = Tweak("hey you, long time", TweakKind.FUNNIER))
        assertTrue(p.userContent.contains("Draft reply for Me: \"hey you, long time\""))
        assertTrue(p.userContent.endsWith("Rewrite this draft to be funnier. Keep its meaning. Give 3 variations."))
        assertFalse(p.userContent.contains("Write 3 replies for Me to send next."))
        // Angle labels instead of the casual/warm/short trio, and the tweak decides length.
        assertFalse(p.systemInstruction.contains(""""casual", "warm", "short""""))
        assertTrue(p.systemInstruction.contains("as the rewrite request below says"))
    }

    @Test
    fun tweakDraftIsRedacted() {
        val p = PromptBuilder.build(settings, chat, tweak = Tweak("call me 0971234567", TweakKind.SHORTER))
        assertTrue(p.userContent.contains("\"call me [PHONE]\""))
    }

    @Test
    fun tweakLabels() {
        assertEquals(listOf("Shorter", "Funnier", "Bolder", "Softer", "+ Emoji"), TweakKind.entries.map { it.label })
    }
}
