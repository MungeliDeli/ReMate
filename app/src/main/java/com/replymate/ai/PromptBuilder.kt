package com.replymate.ai

import com.replymate.data.AppSettings
import com.replymate.data.Persona
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

    /**
     * [persona] is the writing style for this reply (the default unless the
     * owner picked another or set one for this contact). [contactNote] is the
     * owner's note about the other person. [tweak] rewrites one draft reply.
     */
    fun build(
        settings: AppSettings,
        conversation: Conversation,
        options: ReplyOptions = ReplyOptions(),
        persona: Persona = settings.defaultPersona,
        contactNote: String? = null,
        tweak: Tweak? = null,
    ): Prompt {
        val name = settings.displayName.trim().ifEmpty { "the user" }
        val app = when (conversation.app) {
            SupportedApp.WHATSAPP -> "WhatsApp"
            SupportedApp.MESSENGER -> "Messenger"
        }
        val other = otherLabel(settings, conversation.contactName)

        val system = buildString {
            append("You help $name reply to messages on $app. Write exactly as $name would write.\n")
            section("ABOUT $name", persona.aboutMe)
            section("HOW $name WRITES", persona.writingStyle)
            section("THINGS $name AVOIDS", persona.avoid)
            val examples = persona.exampleList
            if (examples.isNotEmpty()) {
                append("\nREAL EXAMPLES OF $name'S MESSAGES (copy this voice, length, spelling and emoji habits):\n")
                examples.forEach { append("- ").append(it).append('\n') }
            }
            contactNote?.let { section("$name'S NOTE ABOUT ${other.uppercase()} (use it to pick the right tone; don't quote it)", Redactor.redact(it)) }
            append("\nRULES:\n")
            if (options.isAuto && tweak == null) {
                append("- Give exactly 3 different reply options with these tones: ")
                append(TONES.joinToString(", ") { "\"$it\"" }).append(".\n")
            } else {
                append("- Give exactly 3 different reply options.")
                if (!options.isAuto) append(" For this reply, the owner asked for:")
                append('\n')
                if (options.moods.isNotEmpty()) {
                    append("  - Mood: ").append(options.moods.joinToString(" and ") { it.instruction }).append('\n')
                }
                options.vibe?.let { append("  - Vibe: ").append(it.instruction).append('\n') }
                append("- All 3 options follow that request but must be clearly different from each other in angle and wording. ")
                append("For \"tone\", give each a 1-3 word label for its angle, e.g. \"teasing\", \"smooth\", \"bold\".\n")
                append("- Stay in $name's voice: the request changes the mood of this reply, not who $name is.\n")
                append("- If the requested mood clearly doesn't fit the latest message (e.g. flirting when they share bad news), stay respectful and lean towards kindness.\n")
            }
            append("- Reply to the latest message(s) from $other, using the earlier context.\n")
            val length = options.length
            if (tweak != null) {
                append("- Length: as the rewrite request below says (it overrides other length or tone rules).\n")
            } else if (length == null) {
                append("- Match $name's typical message length. Never write more than 3 sentences.\n")
            } else {
                append("- Length: ${length.instruction}.\n")
            }
            append("- Language: ${languageRule(persona.replyLanguage)}\n")
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
            if (tweak != null) {
                append("\nDraft reply for Me: \"").append(Redactor.redact(tweak.draft).replace('\n', ' ')).append("\"\n")
                append("Rewrite this draft to be ${tweak.kind.instruction}. Keep its meaning. Give 3 variations.")
            } else {
                append("\nWrite 3 replies for Me to send next.")
                if (conversation.messages.lastOrNull()?.sender == Sender.ME) {
                    append(" Me sent the last message; suggest natural follow-ups.")
                }
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
