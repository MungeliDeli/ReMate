package com.replymate.ai

import com.replymate.Config
import kotlinx.serialization.json.Json

/** Turns a generateContent response body into suggestions (spec F9 "Response handling"). */
object ResponseParser {

    sealed interface Outcome {
        data class Suggestions(val list: List<Suggestion>) : Outcome
        data object Blocked : Outcome
        data class Unreadable(val reason: String) : Outcome
    }

    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val safetyFinish = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "IMAGE_SAFETY")

    fun parse(body: String): Outcome {
        val response = try {
            json.decodeFromString(GenerateResponse.serializer(), body)
        } catch (e: Exception) {
            return Outcome.Unreadable("response is not valid JSON")
        }
        if (!response.promptFeedback?.blockReason.isNullOrBlank()) return Outcome.Blocked
        val candidate = response.candidates.firstOrNull() ?: return Outcome.Unreadable("no candidates")
        if (candidate.finishReason in safetyFinish) return Outcome.Blocked

        val text = candidate.content?.parts.orEmpty()
            .filter { it.thought != true }
            .mapNotNull { it.text }
            .joinToString("")
        if (text.isBlank()) {
            return Outcome.Unreadable("empty answer (finishReason=${candidate.finishReason ?: "?"})")
        }
        val list = suggestionsFromText(text)
        return if (list.isEmpty()) Outcome.Unreadable("no suggestions in answer") else Outcome.Suggestions(list)
    }

    /** JSON first; if that fails, best-effort one suggestion per line (spec F9). */
    fun suggestionsFromText(raw: String): List<Suggestion> {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val fromJson = runCatching { json.decodeFromString(SuggestionList.serializer(), text).suggestions }
            .recoverCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Suggestion.serializer()), text) }
            .getOrNull()
        val list = fromJson ?: fromLines(text)
        return list
            .map { Suggestion(it.tone.trim().lowercase().ifEmpty { "reply" }, it.text.trim()) }
            .filter { it.text.isNotEmpty() }
            .take(Config.SUGGESTION_COUNT)
    }

    private val bullet = Regex("""^\s*(?:[-*•]|\d+[.)])\s*""")
    private val toneLabel = Regex("""^\s*\(?(casual|warm|short)\)?\s*[:\-–]\s*""", RegexOption.IGNORE_CASE)

    private fun fromLines(text: String): List<Suggestion> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.any(Char::isLetterOrDigit) }
            .filterNot { it.startsWith("{") || it.startsWith("}") || it.startsWith("[") || it.startsWith("]") }
            .mapIndexed { i, line ->
                var t = line.replace(bullet, "")
                val tone = toneLabel.find(t)?.groupValues?.get(1)?.lowercase()
                t = t.replace(toneLabel, "").trim().trim('"', '“', '”')
                Suggestion(tone ?: PromptBuilder.TONES.getOrElse(i) { "reply" }, t)
            }
}
