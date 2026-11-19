package com.replymate.ai

import com.replymate.data.AppSettings
import com.replymate.data.Persona
import com.replymate.debug.DebugLog
import com.replymate.debug.DebugType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReplySuggesterTest {
    private lateinit var server: MockWebServer
    private val key = "AIzaFAKEKEYfortests1234567890"
    private val settings = AppSettings(displayName = "Sam", apiKey = key, personas = listOf(Persona(examples = "ok\nlol")))

    @Before
    fun setUp() {
        DebugLog.clear()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun suggester() = ReplySuggester(GeminiClient(GeminiClient.defaultHttpClient(), server.url("/").toString(), retryDelayMs = 1))

    @Test
    fun debugLogShowsRedactedPromptButNeverTheKey() {
        // An error body that (hypothetically) echoes the key must still be masked.
        server.enqueue(MockResponse.Builder().code(403).body("""{"error":{"message":"key $key blocked"}}""").build())
        val r = runBlocking { suggester().suggest(settings, SampleChat.conversation) }
        assertEquals(GeminiError.AUTH, (r as GeminiResult.Failure).error)

        val entries = DebugLog.entries.value
        val request = entries.single { it.type == DebugType.REQUEST }
        assertTrue("phone redacted in Debug", request.body.contains("call me on [PHONE]"))
        assertFalse(request.body.contains("0971234567"))
        entries.forEach { assertFalse("key leaked in ${it.type}", it.title.contains(key) || it.body.contains(key)) }
        assertTrue(entries.any { it.type == DebugType.ERROR && it.body.contains("****") })
    }

    @Test
    fun noKeyMeansNoNetworkCall() {
        val r = runBlocking { suggester().suggest(settings.copy(apiKey = ""), SampleChat.conversation) }
        assertEquals(GeminiError.NO_KEY, (r as GeminiResult.Failure).error)
        assertEquals(0, server.requestCount)
    }
}
