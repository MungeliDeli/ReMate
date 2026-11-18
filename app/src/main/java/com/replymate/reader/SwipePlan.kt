package com.replymate.reader

import com.replymate.Config

/**
 * Geometry for the scroll fallback swipe (used where the chat list doesn't
 * offer a scroll action, e.g. Messenger). Kept pure so a test can prove the
 * swipe is always a straight vertical drag inside the message area: it can
 * scroll, never tap.
 */
object SwipePlan {

    data class Swipe(val x: Float, val fromY: Float, val toY: Float)

    /** The message area: below the toolbar, above the composer (or the lower screen). */
    fun listArea(screenWidth: Int, screenHeight: Int, composerTop: Int?): Box {
        val top = (screenHeight * (Config.TOOLBAR_HEIGHT_RATIO + 0.04f)).toInt()
        val bottom = ((composerTop ?: (screenHeight * 0.8f).toInt()) - 16).coerceAtLeast(top)
        return Box(0, top, screenWidth, bottom)
    }

    /**
     * [older] = true: finger moves DOWN, so the list shows older messages.
     * Uses the middle half of the area so it never starts on the toolbar or composer.
     */
    fun forArea(area: Box, older: Boolean): Swipe? {
        if (area.height < MIN_AREA_HEIGHT || area.width <= 0) return null
        val x = area.left + area.width * 0.5f
        val upper = area.top + area.height * 0.25f
        val lower = area.top + area.height * 0.75f
        return if (older) Swipe(x, upper, lower) else Swipe(x, lower, upper)
    }

    const val MIN_AREA_HEIGHT = 300
}
