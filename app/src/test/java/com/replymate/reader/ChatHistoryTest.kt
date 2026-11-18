package com.replymate.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryTest {
    private fun them(t: String) = ChatMessage(Sender.THEM, t)
    private fun me(t: String) = ChatMessage(Sender.ME, t)

    @Test
    fun joinsOverlappingScreensOnce() {
        val oldest = listOf(them("a"), me("b"), them("c"), me("d"))
        val middle = listOf(them("c"), me("d"), them("e"), me("f"))
        val newest = listOf(me("f"), them("g"), me("h"))
        assertEquals(
            listOf("a", "b", "c", "d", "e", "f", "g", "h"),
            ChatHistory.merge(listOf(oldest, middle, newest), 40).map { it.text },
        )
    }

    @Test
    fun noOverlapJustAppends() {
        assertEquals(4, ChatHistory.merge(listOf(listOf(them("a"), me("b")), listOf(them("c"), me("d"))), 40).size)
    }

    @Test
    fun overlapNeedsSameSender() {
        // Same text but different sender is a different message.
        assertEquals(0, ChatHistory.overlap(listOf(them("ok")), listOf(me("ok"))))
        assertEquals(1, ChatHistory.overlap(listOf(them("x"), me("ok")), listOf(me("ok"), them("y"))))
    }

    @Test
    fun keepsNewestMax() {
        val page = (1..30).map { them("m$it") }
        val older = (1..30).map { me("o$it") }
        val merged = ChatHistory.merge(listOf(older, page), 40)
        assertEquals(40, merged.size)
        assertEquals("m30", merged.last().text)
        assertTrue(merged.first().text.startsWith("o"))
    }
}

class SwipePlanTest {
    @Test
    fun swipeIsVerticalInsideMessageArea() {
        val area = SwipePlan.listArea(1080, 2400, composerTop = 2180)
        for (older in listOf(true, false)) {
            val s = SwipePlan.forArea(area, older)!!
            assertEquals(540f, s.x, 0.01f)
            assertTrue(s.fromY in area.top.toFloat()..area.bottom.toFloat())
            assertTrue(s.toY in area.top.toFloat()..area.bottom.toFloat())
        }
        // Older = finger moves down; newer = finger moves up.
        assertTrue(SwipePlan.forArea(area, true)!!.let { it.toY > it.fromY })
        assertTrue(SwipePlan.forArea(area, false)!!.let { it.toY < it.fromY })
    }

    @Test
    fun areaStaysBelowToolbarAndAboveComposer() {
        val area = SwipePlan.listArea(1080, 2400, composerTop = 1200) // keyboard open
        assertTrue(area.top >= 288) // below the 12% toolbar band
        assertTrue(area.bottom < 1200)
    }

    @Test
    fun tooSmallAreaMeansNoSwipe() {
        assertEquals(null, SwipePlan.forArea(Box(0, 500, 1080, 600), true))
    }
}
