package com.replymate.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParserTest {
    private fun wrap(text: String, finish: String = "STOP"): String {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"candidates":[{"content":{"role":"model","parts":[{"text":"$escaped"}]},"finishReason":"$finish"}],"usageMetadata":{"totalTokenCount":120}}"""
    }

    @Test
    fun parsesStructuredJson() {
        val body = wrap("""{"suggestions":[{"tone":"casual","text":"yeah 7 works"},{"tone":"warm","text":"wouldn't miss it ❤️"},{"tone":"short","text":"coming"}]}""")
        val out = ResponseParser.parse(body) as ResponseParser.Outcome.Suggestions
        assertEquals(listOf("casual", "warm", "short"), out.list.map { it.tone })
        assertEquals("wouldn't miss it ❤️", out.list[1].text)
    }

    @Test
    fun takesFirstThree() {
        val five = (1..5).joinToString(",") { """{"tone":"t$it","text":"r$it"}""" }
        val out = ResponseParser.parse(wrap("""{"suggestions":[$five]}""")) as ResponseParser.Outcome.Suggestions
        assertEquals(listOf("r1", "r2", "r3"), out.list.map { it.text })
    }

    @Test
    fun skipsThoughtPartsAndCodeFences() {
        val body = """{"candidates":[{"content":{"parts":[{"text":"thinking...","thought":true},{"text":"```json\n{\"suggestions\":[{\"tone\":\"short\",\"text\":\"ok\"}]}\n```"}]},"finishReason":"STOP"}]}"""
        val out = ResponseParser.parse(body) as ResponseParser.Outcome.Suggestions
        assertEquals("ok", out.list.single().text)
    }

    @Test
    fun fallsBackToLinesWhenNotJson() {
        val out = ResponseParser.parse(wrap("1. Casual: yeah I'll be there\n2. Warm: can't wait to see you\n3. \"see you at 7\"")) as ResponseParser.Outcome.Suggestions
        assertEquals(
            listOf("casual" to "yeah I'll be there", "warm" to "can't wait to see you", "short" to "see you at 7"),
            out.list.map { it.tone to it.text },
        )
    }

    @Test
    fun blockedPromptAndSafetyFinish() {
        assertEquals(ResponseParser.Outcome.Blocked, ResponseParser.parse("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        assertEquals(ResponseParser.Outcome.Blocked, ResponseParser.parse("""{"candidates":[{"finishReason":"SAFETY"}]}"""))
        assertEquals(ResponseParser.Outcome.Blocked, ResponseParser.parse("""{"candidates":[{"finishReason":"PROHIBITED_CONTENT"}]}"""))
    }

    @Test
    fun unreadableCases() {
        assertTrue(ResponseParser.parse("not json") is ResponseParser.Outcome.Unreadable)
        assertTrue(ResponseParser.parse("""{"candidates":[]}""") is ResponseParser.Outcome.Unreadable)
        assertTrue(ResponseParser.parse("""{"candidates":[{"content":{"parts":[]},"finishReason":"MAX_TOKENS"}]}""") is ResponseParser.Outcome.Unreadable)
        assertTrue(ResponseParser.parse(wrap("""{"suggestions":[]}""")) is ResponseParser.Outcome.Unreadable)
    }
}
