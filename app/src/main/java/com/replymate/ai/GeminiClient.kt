package com.replymate.ai

import com.replymate.Config
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Calls Gemini generateContent over plain REST (spec F9). Verified against
 * ai.google.dev on 2026-11-18:
 * - POST /v1beta/models/{model}:generateContent, header x-goog-api-key
 * - structured output: generationConfig.responseMimeType + responseJsonSchema
 *   (the spec's responseSchema is now documented as internal)
 * - thinking: generationConfig.thinkingConfig.thinkingLevel. MINIMAL is the
 *   cheapest, but not every model accepts it, so on a "thinking" 400 error
 *   it retries with LOW, then with no thinking config.
 *
 * Retries: one retry after [retryDelayMs] on network errors, 429, 500 and 503.
 * Cancelling the coroutine cancels the HTTP call (panel closed).
 */
class GeminiClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = Config.GEMINI_BASE_URL,
    private val retryDelayMs: Long = Config.GEMINI_RETRY_DELAY_MS,
) {
    private val thinkingLevels = listOf("MINIMAL", "LOW", null)
    private val validModel = Regex("""^[A-Za-z0-9._\-]+$""")

    suspend fun generate(apiKey: String, modelId: String, prompt: Prompt): GeminiResult {
        if (apiKey.isBlank()) return GeminiResult.Failure(GeminiError.NO_KEY)
        if (!validModel.matches(modelId)) return GeminiResult.Failure(GeminiError.MODEL, "invalid model ID '$modelId'")

        val started = System.currentTimeMillis()
        var level = 0
        var retried = false
        while (true) {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1beta/models/$modelId:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(requestBody(prompt, thinkingLevels[level]).toString().toRequestBody(JSON))
                .build()

            val (code, body) = try {
                execute(request)
            } catch (e: IOException) {
                if (!retried) {
                    retried = true
                    delay(retryDelayMs)
                    continue
                }
                return GeminiResult.Failure(GeminiError.NETWORK, "${e.javaClass.simpleName}: ${e.message}", latencyMs = elapsed(started))
            }

            fun fail(error: GeminiError, detail: String = "") =
                GeminiResult.Failure(error, detail, code, body, elapsed(started))

            when {
                code in 200..299 -> return when (val outcome = ResponseParser.parse(body)) {
                    is ResponseParser.Outcome.Suggestions -> GeminiResult.Success(outcome.list, code, body, elapsed(started))
                    ResponseParser.Outcome.Blocked -> fail(GeminiError.SAFETY)
                    is ResponseParser.Outcome.Unreadable -> fail(GeminiError.BAD_RESPONSE, outcome.reason)
                }
                code == 400 && looksLikeBadKey(body) -> return fail(GeminiError.AUTH)
                code == 400 && body.contains("thinking", ignoreCase = true) && level < thinkingLevels.lastIndex -> {
                    level++ // model doesn't accept this thinking level: try the next one
                }
                code == 401 || code == 403 -> return fail(GeminiError.AUTH)
                code == 404 -> return fail(GeminiError.MODEL, "model '$modelId' not found")
                code == 429 || code == 500 || code == 503 -> {
                    if (!retried) {
                        retried = true
                        delay(retryDelayMs)
                    } else {
                        return fail(if (code == 429) GeminiError.RATE_LIMIT else GeminiError.SERVER)
                    }
                }
                else -> return fail(GeminiError.SERVER, "HTTP $code")
            }
        }
    }

    /** Gemini answers a wrong key with 400 INVALID_ARGUMENT "API key not valid". */
    private fun looksLikeBadKey(body: String) =
        body.contains("API_KEY_INVALID") || body.contains("API key not valid", ignoreCase = true)

    private suspend fun execute(request: Request): Pair<Int, String> = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { it.code to it.body.string() }
                } catch (e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                    return
                }
                if (cont.isActive) cont.resume(result)
            }
        })
    }

    private fun elapsed(started: Long) = System.currentTimeMillis() - started

    companion object {
        private val JSON = "application/json".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(Config.GEMINI_CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(Config.GEMINI_READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

        /** Request body per spec F9 (field names updated to the current API). */
        fun requestBody(prompt: Prompt, thinkingLevel: String?): JsonObject = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", prompt.systemInstruction) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", prompt.userContent) } }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", Config.GEMINI_TEMPERATURE)
                put(
                    "maxOutputTokens",
                    if (thinkingLevel == "MINIMAL") Config.GEMINI_MAX_OUTPUT_TOKENS else Config.GEMINI_MAX_OUTPUT_TOKENS_WITH_THINKING,
                )
                put("responseMimeType", "application/json")
                putJsonObject("responseJsonSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("suggestions") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("tone") { put("type", "string") }
                                    putJsonObject("text") { put("type", "string") }
                                }
                                putJsonArray("required") { add("tone"); add("text") }
                            }
                        }
                    }
                    putJsonArray("required") { add("suggestions") }
                }
                if (thinkingLevel != null) {
                    putJsonObject("thinkingConfig") { put("thinkingLevel", thinkingLevel) }
                }
            }
        }
    }
}
