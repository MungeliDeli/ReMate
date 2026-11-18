package com.replymate.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleGeometryTest {
    private val w = 1080
    private val h = 2400
    private val size = 176

    @Test
    fun snapsToNearestEdge() {
        assertEquals(Side.LEFT, BubbleGeometry.snapSide(100, w))
        assertEquals(Side.LEFT, BubbleGeometry.snapSide(539, w))
        assertEquals(Side.RIGHT, BubbleGeometry.snapSide(540, w))
        assertEquals(Side.RIGHT, BubbleGeometry.snapSide(1000, w))
    }

    @Test
    fun xSitsFlushOnEachEdge() {
        assertEquals(0, BubbleGeometry.xFor(Side.LEFT, w, size))
        assertEquals(w - size, BubbleGeometry.xFor(Side.RIGHT, w, size))
    }

    @Test
    fun defaultFractionIs40PercentDown() {
        assertEquals(960, BubbleGeometry.yFor(0.40f, h, size))
    }

    @Test
    fun yIsClampedOnScreen() {
        assertEquals(0, BubbleGeometry.yFor(-0.5f, h, size))
        assertEquals(h - size, BubbleGeometry.yFor(1f, h, size))
        assertEquals(h - size, BubbleGeometry.clampY(99999, h, size))
    }

    @Test
    fun fractionRoundTrips() {
        val f = BubbleGeometry.fractionFor(1200, h)
        assertEquals(0.5f, f, 0.0001f)
        assertEquals(1200, BubbleGeometry.yFor(f, h, size))
    }
}
