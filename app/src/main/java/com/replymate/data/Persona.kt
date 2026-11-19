package com.replymate.data

import com.replymate.Config
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ReplyLanguage(val label: String) {
    MATCH_CHAT("Match the chat"),
    ENGLISH("English"),
    MIXED("Mixed (English + local)"),
}

/** One way of writing (spec F8 persona fields). The owner can keep several. */
@Serializable
data class Persona(
    val id: String = DEFAULT_PERSONA_ID,
    val label: String = "My persona",
    val aboutMe: String = "",
    val writingStyle: String = "",
    val examples: String = "", // raw text, one message per line
    val avoid: String = "",
    val replyLanguage: ReplyLanguage = ReplyLanguage.MATCH_CHAT,
) {
    val exampleList: List<String> get() = parseExamples(examples)
}

const val DEFAULT_PERSONA_ID = "p1"

fun newPersonaId(): String = "p" + UUID.randomUUID().toString().replace("-", "").take(10)

/**
 * Everything on the Persona tab. Name, key, model and reading settings are
 * shared; [personas] hold the writing styles, one of them the default.
 */
data class AppSettings(
    val displayName: String = "",
    val apiKey: String = "",
    val modelId: String = Config.DEFAULT_MODEL_ID,
    val shareFirstName: Boolean = false,
    /** "Chat context": how many screens of the chat to read (1-3). */
    val contextScreens: Int = Config.DEFAULT_CONTEXT_SCREENS,
    val personas: List<Persona> = listOf(Persona()),
    val defaultPersonaId: String = DEFAULT_PERSONA_ID,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    val defaultPersona: Persona
        get() = personas.firstOrNull { it.id == defaultPersonaId } ?: personas.firstOrNull() ?: Persona()

    /** The persona with [id], or the default if it no longer exists. */
    fun persona(id: String?): Persona = personas.firstOrNull { it.id == id } ?: defaultPersona

    /** Checklist rule: a name plus a handful of real example messages in the default persona. */
    val personaReady: Boolean
        get() = displayName.isNotBlank() && defaultPersona.exampleList.size >= Config.MIN_EXAMPLES_FOR_READY

    /** Model to call; falls back to the default if the field was cleared. */
    val effectiveModelId: String get() = modelId.trim().ifEmpty { Config.DEFAULT_MODEL_ID }
}

/** Non-blank lines, trimmed, capped at [Config.MAX_EXAMPLE_LINES]. */
fun parseExamples(raw: String): List<String> =
    raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(Config.MAX_EXAMPLE_LINES)

/** Counts non-blank lines without the cap, so the UI can warn when over the limit. */
fun countExampleLines(raw: String): Int = raw.lines().count { it.isNotBlank() }
