package com.replymate.ai

import com.replymate.data.AppSettings
import com.replymate.data.Persona
import com.replymate.data.ReplyLanguage
import com.replymate.reader.ChatMessage
import com.replymate.reader.Conversation
import com.replymate.reader.Sender
import com.replymate.service.SupportedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    private val full = AppSettings(
        displayName = "Sam",
        personas = listOf(
            Persona(
                aboutMe = "20s, student in Lusaka",
                writingStyle = "short, lowercase, light slang",
                examples = "lol ok\n\nsharp sharp\nsee you later",
                avoid = "no formal greetings",
                replyLanguage = ReplyLanguage.MATCH_CHAT,
            ),
        ),
    )
    private val chat = Conversation(
        SupportedApp.WHATSAPP,
        "Chanda Mulenga",
        listOf(
            ChatMessage(Sender.THEM, "hey are you coming tonight?"),
            ChatMessage(Sender.ME, "which place again"),
            ChatMessage(Sender.THEM, "the usual spot at 7, call 0971234567"),
        ),
    )

    @Test
    fun systemInstructionFollowsTemplate() {
        val sys = PromptBuilder.build(full, chat).systemInstruction
        assertTrue(sys.startsWith("You help Sam reply to messages on WhatsApp. Write exactly as Sam would write."))
        assertTrue(sys.contains("ABOUT Sam:\n20s, student in Lusaka"))
        assertTrue(sys.contains("HOW Sam WRITES:\nshort, lowercase, light slang"))
        assertTrue(sys.contains("THINGS Sam AVOIDS:\nno formal greetings"))
        assertTrue(sys.contains("REAL EXAMPLES OF Sam'S MESSAGES (copy this voice, length, spelling and emoji habits):\n- lol ok\n- sharp sharp\n- see you later\n"))
        assertTrue(sys.contains("""- Give exactly 3 different reply options with these tones: "casual", "warm", "short"."""))
        assertTrue(sys.contains("- Language: Match the language and mix used in the conversation."))
        assertTrue(sys.contains("[PHONE], [EMAIL], [LINK], [NUMBER]"))
    }

    @Test
    fun conversationBlockIsRedactedAndUsesThem() {
        val user = PromptBuilder.build(full, chat).userContent
        assertEquals(
            "Conversation (oldest first):\n" +
                "Them: hey are you coming tonight?\n" +
                "Me: which place again\n" +
                "Them: the usual spot at 7, call [PHONE]\n" +
                "\nWrite 3 replies for Me to send next.",
            user,
        )
        assertFalse(user.contains("Chanda"))
    }

    @Test
    fun lastMessageFromMeAddsFollowUpHint() {
        val c = chat.copy(messages = chat.messages + ChatMessage(Sender.ME, "ok coming"))
        assertTrue(PromptBuilder.build(full, c).userContent.endsWith("Me sent the last message; suggest natural follow-ups."))
    }

    @Test
    fun emptyPersonaSectionsAreOmitted() {
        val sys = PromptBuilder.build(AppSettings(), chat).systemInstruction
        assertTrue(sys.startsWith("You help the user reply"))
        listOf("ABOUT", "HOW ", "AVOIDS", "REAL EXAMPLES").forEach { assertFalse(it, sys.contains(it)) }
        assertTrue(sys.contains("RULES:"))
    }

    @Test
    fun shareFirstNameSendsOnlyFirstWord() {
        val s = full.copy(shareFirstName = true)
        val p = PromptBuilder.build(s, chat)
        assertTrue(p.userContent.contains("Chanda: hey are you coming tonight?"))
        assertFalse(p.userContent.contains("Mulenga"))
        assertTrue(p.systemInstruction.contains("latest message(s) from Chanda"))
        assertEquals("Them", PromptBuilder.otherLabel(s, null))
        assertEquals("Them", PromptBuilder.otherLabel(s, "+260 97 123 4567"))
    }

    @Test
    fun languageRules() {
        assertEquals("Reply in English.", PromptBuilder.languageRule(ReplyLanguage.ENGLISH))
        assertEquals(
            "Reply in a natural mix of English and the local language used in the examples.",
            PromptBuilder.languageRule(ReplyLanguage.MIXED),
        )
    }
}
