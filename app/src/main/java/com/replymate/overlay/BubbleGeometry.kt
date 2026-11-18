package com.replymate.overlay

enum class Side { LEFT, RIGHT }

/** Where the bubble sits: which edge, and how far down (0 = top, 1 = bottom). */
data class BubblePosition(val side: Side, val yFraction: Float)

/** Pure position maths for the bubble, kept separate so it can be unit-tested. */
object BubbleGeometry {

    /** Snap to whichever side edge the bubble's centre is nearer to. */
    fun snapSide(centerX: Int, screenWidth: Int): Side =
        if (centerX < screenWidth / 2) Side.LEFT else Side.RIGHT

    /** Window x (from the left) for a bubble window of [windowSize] on [side]. */
    fun xFor(side: Side, screenWidth: Int, windowSize: Int): Int =
        if (side == Side.LEFT) 0 else (screenWidth - windowSize).coerceAtLeast(0)

    /** Window y for a stored fraction, kept fully on screen. */
    fun yFor(yFraction: Float, screenHeight: Int, windowSize: Int): Int =
        clampY((yFraction * screenHeight).toInt(), screenHeight, windowSize)

    fun clampY(y: Int, screenHeight: Int, windowSize: Int): Int =
        y.coerceIn(0, (screenHeight - windowSize).coerceAtLeast(0))

    fun fractionFor(y: Int, screenHeight: Int): Float =
        if (screenHeight <= 0) 0f else (y.toFloat() / screenHeight).coerceIn(0f, 1f)
}
