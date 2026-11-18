package com.replymate.overlay

import com.replymate.ai.GeminiError
import com.replymate.ai.GeminiResult
import com.replymate.ai.ReplyOptions
import com.replymate.ai.Suggestion

/** What the button under an error message does. */
enum class PanelAction(val label: String) {
    RETRY("Retry"),
    OPEN_SETTINGS("Open settings"),
}

/** The panel states: the four from spec F5 plus style options and chat reading. */
sealed interface PanelState {
    /** Style picker shown first: mood / length / vibe, then "Suggest". */
    data class Options(val options: ReplyOptions) : PanelState

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
        ): PanelState = when (result) {
            is GeminiResult.Success -> Results(
                result.suggestions,
                hint = if (personaHasExamples) null else EMPTY_PERSONA_HINT,
                summary = listOfNotNull(
                    options.summary().takeIf { !options.isAuto },
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
