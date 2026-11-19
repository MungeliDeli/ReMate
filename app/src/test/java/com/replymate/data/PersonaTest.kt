package com.replymate.data

import com.replymate.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaTest {
    @Test
    fun parseExamples_trimsAndDropsBlankLines() {
        val raw = "  hey whats up \n\n\nlol ok\r\n   \nsharp sharp"
        assertEquals(listOf("hey whats up", "lol ok", "sharp sharp"), parseExamples(raw))
    }

    @Test
    fun parseExamples_capsAtMaxLines() {
        val raw = (1..60).joinToString("\n") { "msg $it" }
        val parsed = parseExamples(raw)
        assertEquals(Config.MAX_EXAMPLE_LINES, parsed.size)
        assertEquals("msg 1", parsed.first())
        assertEquals(60, countExampleLines(raw))
    }

    @Test
    fun personaReady_needsNameAndMinimumExamples() {
        val examples = (1..Config.MIN_EXAMPLES_FOR_READY).joinToString("\n") { "m$it" }
        assertFalse(AppSettings(personas = listOf(Persona(examples = examples))).personaReady)
        assertFalse(AppSettings(displayName = "Sam", personas = listOf(Persona(examples = "just one"))).personaReady)
        assertTrue(AppSettings(displayName = "Sam", personas = listOf(Persona(examples = examples))).personaReady)
        // Only the DEFAULT persona counts.
        val two = AppSettings(
            displayName = "Sam",
            personas = listOf(Persona(id = "a", examples = "x"), Persona(id = "b", examples = examples)),
            defaultPersonaId = "a",
        )
        assertFalse(two.personaReady)
        assertTrue(two.copy(defaultPersonaId = "b").personaReady)
    }

    @Test
    fun apiKeyAndModelDefaults() {
        assertFalse(AppSettings(apiKey = "   ").hasApiKey)
        assertTrue(AppSettings(apiKey = "abc").hasApiKey)
        assertEquals(Config.DEFAULT_MODEL_ID, AppSettings(modelId = "  ").effectiveModelId)
        assertEquals("gemini-x", AppSettings(modelId = " gemini-x ").effectiveModelId)
    }
}
