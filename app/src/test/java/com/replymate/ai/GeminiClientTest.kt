package com.replymate.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GeminiClient
    private val prompt = Prompt("SYSTEM TEXT", "USER TEXT")
    private val okBody = """{"candidates":[{"content":{"parts":[{"text":"{\"suggestions\":[{\"tone\":\"casual\",\"text\":\"yo\"},{\"tone\":\"warm\",\"text\":\"hey friend\"},{\"tone\":\"short\",\"text\":\"k\"}]}"}]},"finishReason":"STOP"}]}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GeminiClient(GeminiClient.defaultHttpClient(), server.url("/").toString(), retryDelayMs = 10)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(code: Int, body: String = "{}") =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun run() = runBlocking { client.generate("KEY123456", "gemini-3.5-flash-lite", prompt) }

    @Test
    fun successSendsCorrectRequest() {
        respond(200, okBody)
        val r = run() as GeminiResult.Success
        assertEquals(listOf("yo", "hey friend", "k"), r.suggestions.map { it.text })

        val req = server.takeRequest()
        assertEquals("/v1beta/models/gemini-3.5-flash-lite:generateContent", req.url.encodedPath)
        assertEquals("KEY123456", req.headers["x-goog-api-key"])
        assertTrue(req.url.toString().contains("key=").not()) // key never in the URL
        val json = Json.parseToJsonElement(req.body!!.utf8()).jsonObject
        assertEquals("SYSTEM TEXT", json["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val gen = json["generationConfig"]!!.jsonObject
        assertEquals("application/json", gen["responseMimeType"]!!.jsonPrimitive.content)
        assertTrue(gen.containsKey("responseJsonSchema"))
        assertEquals("400", gen["maxOutputTokens"]!!.jsonPrimitive.content)
        assertEquals("MINIMAL", gen["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content)
    }

    @Test
    fun retriesOnceOn429ThenSucceeds() {
        respond(429)
        respond(200, okBody)
        assertTrue(run() is GeminiResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun twoRateLimitsGiveRateLimitError() {
        respond(429); respond(429)
        assertEquals(GeminiError.RATE_LIMIT, (run() as GeminiResult.Failure).error)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun serverErrorsRetryOnce() {
        respond(503); respond(500)
        assertEquals(GeminiError.SERVER, (run() as GeminiResult.Failure).error)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun badKeyIsAuthWithoutRetry() {
        respond(400, """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}""")
        assertEquals(GeminiError.AUTH, (run() as GeminiResult.Failure).error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun forbiddenIsAuth() {
        respond(403)
        assertEquals(GeminiError.AUTH, (run() as GeminiResult.Failure).error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun unknownModelIs404() {
        respond(404)
        assertEquals(GeminiError.MODEL, (run() as GeminiResult.Failure).error)
    }

    @Test
    fun unsupportedThinkingLevelFallsBackToLowThenNone() {
        respond(400, """{"error":{"message":"thinking_level MINIMAL is not supported for this model"}}""")
        respond(400, """{"error":{"message":"Thinking level is not supported for this model"}}""")
        respond(200, okBody)
        assertTrue(run() is GeminiResult.Success)
        val levels = (1..3).map {
            Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject["generationConfig"]!!.jsonObject["thinkingConfig"]
                ?.jsonObject?.get("thinkingLevel")?.jsonPrimitive?.content
        }
        assertEquals(listOf("MINIMAL", "LOW", null), levels)
    }

    @Test
    fun safetyBlock() {
        respond(200, """{"promptFeedback":{"blockReason":"SAFETY"}}""")
        assertEquals(GeminiError.SAFETY, (run() as GeminiResult.Failure).error)
    }

    @Test
    fun noKeyMakesNoCall() {
        val r = runBlocking { client.generate("  ", "gemini-3.5-flash-lite", prompt) }
        assertEquals(GeminiError.NO_KEY, (r as GeminiResult.Failure).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun networkFailureRetriesOnceThenNoConnection() {
        server.close() // nothing listening any more
        val r = run() as GeminiResult.Failure
        assertEquals(GeminiError.NETWORK, r.error)
    }

    @Test
    fun maskHidesKey() {
        assertEquals("key=**** end", ReplySuggester.mask("key=AIzaSECRET end", "AIzaSECRET"))
    }
}
