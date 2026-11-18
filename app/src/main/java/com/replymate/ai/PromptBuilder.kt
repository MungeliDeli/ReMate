package com.replymate.ai

import com.replymate.data.AppSettings
import com.replymate.data.ReplyLanguage
import com.replymate.reader.Conversation
import com.replymate.reader.Sender
import com.replymate.service.SupportedApp

/** What is sent to Gemini: a system instruction and one user message. */
data class Prompt(val systemInstruction: String, val userContent: String) {
    /** For the Debug log (never contains the API key). */
    fun describe(): String = "SYSTEM:\n$systemInstruction\n\nUSER:\n$userContent"
}

/**
 * Builds the prompt from the persona and a conversation (spec 8.2 / 8.3).
 * Empty persona sections are left out (spec section 12). Every message and the
 * contact name pass through [Redactor] first (spec F7).
 */
object PromptBuilder {

    val TONES = listOf("casual", "warm", "short")

    fun build(settings: AppSettings, conversation: Conversation): Prompt {
        val name = settings.displayName.trim().ifEmpty { "the user" }
        val app = when (conversation.app) {
            SupportedApp.WHATSAPP -> "WhatsApp"
            SupportedApp.MESSENGER -> "Messenger"
        }
        val other = otherLabel(settings, conversation.contactName)

        val system = buildString {
            append("You help $name reply to messages on $app. Write exactly as $name would write.\n")
            section("ABOUT $name", settings.aboutMe)
            section("HOW $name WRITES", settings.writingStyle)
            section("THINGS $name AVOIDS", settings.avoid)
            val examples = settings.exampleList
            if (examples.isNotEmpty()) {
                append("\nREAL EXAMPLES OF $name'S MESSAGES (copy this voice, length, spelling and emoji habits):\n")
                examples.forEach { append("- ").append(it).append('\n') }
            }
            append("\nRULES:\n")
            append("- Give exactly 3 different reply options with these tones: ")
            append(TONES.joinToString(", ") { "\"$it\"" }).append(".\n")
            append("- Reply to the latest message(s) from $other, using the earlier context.\n")
            append("- Match $name's typical message length. Never write more than 3 sentences.\n")
            append("- Language: ${languageRule(settings.replyLanguage)}\n")
            append("- Never invent facts, plans, promises or personal details that are not in the chat or the profile.\n")
            append("- Placeholders like [PHONE], [EMAIL], [LINK], [NUMBER] are redacted data; do not repeat them.\n")
            append("- Output only JSON matching the schema.")
        }

        val user = buildString {
            append("Conversation (oldest first):\n")
            for (m in conversation.messages) {
                val who = when (m.sender) {
                    Sender.ME -> "Me"
                    Sender.THEM -> other
                    Sender.UNKNOWN -> "Unknown"
                }
                append(who).append(": ").append(Redactor.redact(m.text).replace('\n', ' ')).append('\n')
            }
            append("\nWrite 3 replies for Me to send next.")
            if (conversation.messages.lastOrNull()?.sender == Sender.ME) {
                append(" Me sent the last message; suggest natural follow-ups.")
            }
        }
        return Prompt(system, user)
    }

    /**
     * "Them" by default. With "Share first name with AI" on, only the first
     * word of the (redacted) contact name is used (spec F7).
     */
    fun otherLabel(settings: AppSettings, contactName: String?): String {
        if (!settings.shareFirstName) return "Them"
        val first = contactName?.let(Redactor::redact)?.trim()?.split(Regex("\\s+"))?.firstOrNull()
        return if (first.isNullOrBlank() || first.startsWith("[") || first == "Me") "Them" else first
    }

    fun languageRule(lang: ReplyLanguage): String = when (lang) {
        ReplyLanguage.MATCH_CHAT -> "Match the language and mix used in the conversation."
        ReplyLanguage.ENGLISH -> "Reply in English."
        ReplyLanguage.MIXED -> "Reply in a natural mix of English and the local language used in the examples."
    }

    private fun StringBuilder.section(title: String, body: String) {
        val text = body.trim()
        if (text.isNotEmpty()) append("\n$title:\n$text\n")
    }
}
