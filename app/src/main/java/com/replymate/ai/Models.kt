package com.replymate.ai

import kotlinx.serialization.Serializable

/** One reply option shown to the owner. */
@Serializable
data class Suggestion(val tone: String, val text: String)

/** The JSON shape Gemini is asked to return (spec F9 responseJsonSchema). */
@Serializable
internal data class SuggestionList(val suggestions: List<Suggestion> = emptyList())

// --- generateContent response (only the fields ReplyMate reads) ---

@Serializable
internal data class GenerateResponse(
    val candidates: List<ResponseCandidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
)

@Serializable
internal data class ResponseCandidate(val content: ResponseContent? = null, val finishReason: String? = null)

@Serializable
internal data class ResponseContent(val parts: List<ResponsePart> = emptyList())

@Serializable
internal data class ResponsePart(val text: String? = null, val thought: Boolean? = null)

@Serializable
internal data class PromptFeedback(val blockReason: String? = null)

/** Error categories, each with the exact text from spec section 12. */
enum class GeminiError(val userMessage: String) {
    NO_KEY("Add your Gemini API key in ReplyMate settings"),
    NETWORK("No connection. Try again."),
    AUTH("Your API key was rejected"),
    RATE_LIMIT("Gemini is busy or the free limit was reached. Try again shortly."),
    SAFETY("Gemini declined to suggest a reply for this chat"),
    MODEL("Model not found. Check the Model ID on the Persona tab."),
    SERVER("Gemini had a problem. Try again."),
    BAD_RESPONSE("Couldn't understand Gemini's answer. Try again."),
}

sealed interface GeminiResult {
    val httpStatus: Int?
    val rawBody: String
    val latencyMs: Long

    data class Success(
        val suggestions: List<Suggestion>,
        override val httpStatus: Int?,
        override val rawBody: String,
        override val latencyMs: Long,
    ) : GeminiResult

    data class Failure(
        val error: GeminiError,
        val detail: String = "",
        override val httpStatus: Int? = null,
        override val rawBody: String = "",
        override val latencyMs: Long = 0,
    ) : GeminiResult
}
