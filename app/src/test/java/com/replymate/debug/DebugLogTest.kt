package com.replymate.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DebugLogTest {
    @Before
    fun reset() = DebugLog.clear()

    @Test
    fun newestFirst() {
        DebugLog.add(DebugType.INFO, "first")
        DebugLog.add(DebugType.INFO, "second")
        assertEquals(listOf("second", "first"), DebugLog.entries.value.map { it.title })
    }

    @Test
    fun keepsOnlyLast50() {
        repeat(70) { DebugLog.add(DebugType.INFO, "e$it") }
        val titles = DebugLog.entries.value.map { it.title }
        assertEquals(DebugLog.CAPACITY, titles.size)
        assertEquals("e69", titles.first())
        assertEquals("e20", titles.last())
    }

    @Test
    fun errorRecordsClassAndMessage() {
        DebugLog.error("Capture", IllegalStateException("stale node"))
        val e = DebugLog.entries.value.single()
        assertEquals(DebugType.ERROR, e.type)
        assertTrue(e.title.contains("IllegalStateException"))
        assertEquals("stale node", e.body)
    }

    @Test
    fun clearEmpties() {
        DebugLog.add(DebugType.INFO, "x")
        DebugLog.clear()
        assertTrue(DebugLog.entries.value.isEmpty())
    }
}
