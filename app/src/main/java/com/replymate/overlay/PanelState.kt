package com.replymate.overlay

import com.replymate.ai.GeminiError
import com.replymate.ai.GeminiResult
import com.replymate.ai.Suggestion

/** What the button under an error message does. */
enum class PanelAction(val label: String) {
    RETRY("Retry"),
    OPEN_SETTINGS("Open settings"),
}

/** The four panel states from spec F5. */
sealed interface PanelState {
    data object Loading : PanelState

    /** [hint] e.g. "Add example messages for better replies" (spec section 12). */
    data class Results(val suggestions: List<Suggestion>, val hint: String? = null) : PanelState

    data class Error(val message: String, val action: PanelAction?) : PanelState

    /** "Couldn't read this chat" + "Open debug". */
    data object Empty : PanelState

    companion object {
        const val EMPTY_PERSONA_HINT = "Add example messages for better replies"

        /** Maps a Gemini result to a panel state using the spec section 12 messages. */
        fun fromResult(result: GeminiResult, personaHasExamples: Boolean): PanelState = when (result) {
            is GeminiResult.Success -> Results(
                result.suggestions,
                hint = if (personaHasExamples) null else EMPTY_PERSONA_HINT,
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
