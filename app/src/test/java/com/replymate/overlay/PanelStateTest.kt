package com.replymate.overlay

import com.replymate.ai.GeminiError
import com.replymate.ai.GeminiResult
import com.replymate.ai.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelStateTest {
    private val three = listOf(Suggestion("casual", "a"), Suggestion("warm", "b"), Suggestion("short", "c"))

    @Test
    fun successShowsResults() {
        val s = PanelState.fromResult(GeminiResult.Success(three, 200, "{}", 900), personaHasExamples = true)
        assertEquals(PanelState.Results(three, hint = null), s)
    }

    @Test
    fun emptyPersonaAddsHint() {
        val s = PanelState.fromResult(GeminiResult.Success(three, 200, "{}", 900), personaHasExamples = false) as PanelState.Results
        assertEquals("Add example messages for better replies", s.hint)
    }

    @Test
    fun errorsUseSpecMessagesAndActions() {
        fun err(e: GeminiError) = PanelState.fromResult(GeminiResult.Failure(e), true) as PanelState.Error

        assertEquals("Add your Gemini API key in ReplyMate settings", err(GeminiError.NO_KEY).message)
        assertEquals(PanelAction.OPEN_SETTINGS, err(GeminiError.NO_KEY).action)
        assertEquals("No connection. Try again.", err(GeminiError.NETWORK).message)
        assertEquals(PanelAction.RETRY, err(GeminiError.NETWORK).action)
        assertEquals("Your API key was rejected", err(GeminiError.AUTH).message)
        assertEquals(PanelAction.OPEN_SETTINGS, err(GeminiError.AUTH).action)
        assertEquals("Gemini is busy or the free limit was reached. Try again shortly.", err(GeminiError.RATE_LIMIT).message)
        assertEquals(PanelAction.RETRY, err(GeminiError.RATE_LIMIT).action)
        assertEquals("Gemini declined to suggest a reply for this chat", err(GeminiError.SAFETY).message)
        assertNull("safety block: no retry", err(GeminiError.SAFETY).action)
    }
}
