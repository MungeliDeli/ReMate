package com.replymate.overlay

import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression: in Messenger, ReplyMate scrolls with a swipe. If the panel still
 * watched outside touches, it took that swipe as "tapped outside", closed, and
 * cancelled the read (nothing reached Debug).
 */
class PanelFlagsTest {
    private val normal = FLAG_NOT_FOCUSABLE or FLAG_WATCH_OUTSIDE_TOUCH

    @Test
    fun touchThroughStopsWatchingOutsideTaps() {
        val through = OverlayController.panelFlags(normal, through = true)
        assertEquals(FLAG_NOT_TOUCHABLE, through and FLAG_NOT_TOUCHABLE)
        assertEquals(0, through and FLAG_WATCH_OUTSIDE_TOUCH)
        assertEquals(FLAG_NOT_FOCUSABLE, through and FLAG_NOT_FOCUSABLE)
    }

    @Test
    fun backToNormalRestoresOutsideTapToClose() {
        val back = OverlayController.panelFlags(OverlayController.panelFlags(normal, true), through = false)
        assertEquals(normal, back)
    }
}
