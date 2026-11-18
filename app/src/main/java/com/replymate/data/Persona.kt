package com.replymate.data

import com.replymate.Config

enum class ReplyLanguage(val label: String) {
    MATCH_CHAT("Match the chat"),
    ENGLISH("English"),
    MIXED("Mixed (English + local)"),
}

/** Everything the owner sets on the Persona tab (spec F8). */
data class AppSettings(
    val displayName: String = "",
    val aboutMe: String = "",
    val writingStyle: String = "",
    val examples: String = "", // raw text, one message per line
    val avoid: String = "",
    val replyLanguage: ReplyLanguage = ReplyLanguage.MATCH_CHAT,
    val apiKey: String = "",
    val modelId: String = Config.DEFAULT_MODEL_ID,
    val shareFirstName: Boolean = false,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    val exampleList: List<String> get() = parseExamples(examples)

    /** Checklist rule: a name plus a handful of real example messages. */
    val personaReady: Boolean
        get() = displayName.isNotBlank() && exampleList.size >= Config.MIN_EXAMPLES_FOR_READY

    /** Model to call; falls back to the default if the field was cleared. */
    val effectiveModelId: String get() = modelId.trim().ifEmpty { Config.DEFAULT_MODEL_ID }
}

/** Non-blank lines, trimmed, capped at [Config.MAX_EXAMPLE_LINES]. */
fun parseExamples(raw: String): List<String> =
    raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(Config.MAX_EXAMPLE_LINES)

/** Counts non-blank lines without the cap, so the UI can warn when over the limit. */
fun countExampleLines(raw: String): Int = raw.lines().count { it.isNotBlank() }
