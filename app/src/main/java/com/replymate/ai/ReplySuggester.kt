package com.replymate.ai

import com.replymate.data.AppSettings
import com.replymate.debug.DebugLog
import com.replymate.debug.DebugType
import com.replymate.reader.ChatMessage
import com.replymate.reader.Conversation
import com.replymate.reader.Sender
import com.replymate.service.SupportedApp

/**
 * Conversation + persona -> 3 suggestions. Redacts and builds the prompt,
 * calls Gemini, and records request/response in the Debug log (spec section 10).
 * Used by the Persona "Test" button now and by the bubble panel in M7.
 */
class ReplySuggester(private val client: GeminiClient) {

    suspend fun suggest(settings: AppSettings, conversation: Conversation, label: String = "Chat"): GeminiResult {
        val apiKey = settings.apiKey.trim()
        if (apiKey.isEmpty()) {
            DebugLog.add(DebugType.ERROR, "$label: no API key", GeminiError.NO_KEY.userMessage)
            return GeminiResult.Failure(GeminiError.NO_KEY) // no network call (spec section 12)
        }
        val model = settings.effectiveModelId
        val prompt = PromptBuilder.build(settings, conversation)
        DebugLog.add(
            DebugType.REQUEST,
            "$label → $model (${conversation.messages.size} messages)",
            mask(prompt.describe(), apiKey),
        )

        val result = client.generate(apiKey, model, prompt)

        val status = result.httpStatus?.let { "HTTP $it" } ?: "no HTTP response"
        val head = "$status · ${result.latencyMs} ms"
        when (result) {
            is GeminiResult.Success -> DebugLog.add(
                DebugType.RESPONSE,
                "$label: $head · ${result.suggestions.size} suggestions",
                mask(result.rawBody, apiKey),
            )
            is GeminiResult.Failure -> DebugLog.add(
                DebugType.ERROR,
                "$label: ${result.error.name} · $head",
                mask(listOf(result.error.userMessage, result.detail, result.rawBody).filter { it.isNotBlank() }.joinToString("\n\n"), apiKey),
            )
        }
        return result
    }

    companion object {
        /** The API key must never appear in Debug (spec section 9/14). */
        fun mask(text: String, apiKey: String): String =
            if (apiKey.length < 4) text else text.replace(apiKey, "****")
    }
}

/** Fixed sample used by the Persona "Test" button (spec F8, conversation from 8.3). */
object SampleChat {
    val conversation = Conversation(
        app = SupportedApp.WHATSAPP,
        contactName = "Chanda",
        messages = listOf(
            ChatMessage(Sender.THEM, "hey are you coming tonight?"),
            ChatMessage(Sender.ME, "which place again"),
            ChatMessage(Sender.THEM, "the usual spot at 7, bring the speaker lol. call me on 0971234567 if you get lost"),
        ),
    )
}
