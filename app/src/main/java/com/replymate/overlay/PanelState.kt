package com.replymate.overlay

import com.replymate.ai.GeminiError
import com.replymate.ai.GeminiResult
import com.replymate.ai.ReplyOptions
import com.replymate.ai.Suggestion
import com.replymate.ai.TweakKind

/** What the button under an error message does. */
enum class PanelAction(val label: String) {
    RETRY("Retry"),
    OPEN_SETTINGS("Open settings"),
}

/** Everything the style picker shows and returns. */
data class PanelSetup(
    val options: ReplyOptions = ReplyOptions(),
    /** (id, label) of every persona; chips shown when there is more than one. */
    val personas: List<Pair<String, String>> = emptyList(),
    val personaId: String = "",
    /** Chat title, if it could be read; enables the note and "Remember for". */
    val contactName: String? = null,
    val note: String? = null,
    /** "Remember for <name>": store persona + style for this contact. */
    val remember: Boolean = false,
) {
    val contactFirstName: String? get() = contactName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
}

/** The panel states: the four from spec F5 plus style options and chat reading. */
sealed interface PanelState {
    /** Style picker shown first: persona / mood / length / vibe, then "Suggest". */
    data class Options(val setup: PanelSetup) : PanelState

    /** Scrolling up and reading more of the chat. */
    data class Reading(val screens: Int) : PanelState

    data object Loading : PanelState

    /**
     * [hint] e.g. "Add example messages for better replies" (spec section 12).
     * [summary] the style used, e.g. "Flirty · Short".
     */
    data class Results(
        val suggestions: List<Suggestion>,
        val hint: String? = null,
        val summary: String? = null,
    ) : PanelState

    data class Error(val message: String, val action: PanelAction?) : PanelState

    /** "Couldn't read this chat" + "Open debug". */
    data object Empty : PanelState

    companion object {
        const val EMPTY_PERSONA_HINT = "Add example messages for better replies"

        /** Maps a Gemini result to a panel state using the spec section 12 messages. */
        fun fromResult(
            result: GeminiResult,
            personaHasExamples: Boolean,
            options: ReplyOptions = ReplyOptions(),
            messageCount: Int? = null,
            personaLabel: String? = null,
            noteUsed: Boolean = false,
            tweak: TweakKind? = null,
        ): PanelState = when (result) {
            is GeminiResult.Success -> Results(
                result.suggestions,
                hint = if (personaHasExamples) null else EMPTY_PERSONA_HINT,
                summary = listOfNotNull(
                    tweak?.let { "${it.label} versions" },
                    personaLabel,
                    options.summary().takeIf { !options.isAuto },
                    "note used".takeIf { noteUsed },
                    messageCount?.let { "$it messages read" },
                ).joinToString(" · ").ifEmpty { null },
            )
            is GeminiResult.Failure -> Error(result.error.userMessage, actionFor(result.error))
        }

        fun actionFor(error: GeminiError): PanelAction? = when (error) {
            GeminiError.NO_KEY, GeminiError.AUTH, GeminiError.MODEL -> PanelAction.OPEN_SETTINGS
            GeminiError.SAFETY -> null // spec: no retry
            GeminiError.NETWORK, GeminiError.RATE_LIMIT, GeminiError.SERVER, GeminiError.BAD_RESPONSE -> PanelAction.RETRY
        }
    }
}
