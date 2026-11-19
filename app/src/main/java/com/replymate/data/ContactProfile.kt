package com.replymate.data

import com.replymate.ai.ReplyOptions
import kotlinx.serialization.Serializable

/**
 * The owner's setup for one chat partner, matched by the chat title. Holds
 * only what the owner typed or chose (never chat messages): a note, a persona
 * and a default style.
 */
@Serializable
data class ContactProfile(
    val name: String,
    val note: String = "",
    /** Null = use the default persona. */
    val personaId: String? = null,
    /** Null = use the last style picked in the panel. */
    val options: ReplyOptions? = null,
) {
    val key: String get() = contactKey(name)
    val isEmpty: Boolean get() = note.isBlank() && personaId == null && options == null
}

/** Chat titles are matched case- and spacing-insensitively. */
fun contactKey(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

object ContactProfiles {
    /**
     * Panel "Remember for <name>": on stores this persona + style for the
     * contact; off clears them but keeps the note. Returns null when nothing is
     * left to keep (delete the profile).
     */
    fun applyRemember(
        existing: ContactProfile?,
        name: String,
        remember: Boolean,
        personaId: String,
        options: ReplyOptions,
    ): ContactProfile? {
        val base = existing ?: ContactProfile(name)
        val updated = if (remember) base.copy(personaId = personaId, options = options)
        else base.copy(personaId = null, options = null)
        return updated.takeUnless { it.isEmpty }
    }
}
