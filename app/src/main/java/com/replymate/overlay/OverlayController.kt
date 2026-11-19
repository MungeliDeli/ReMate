package com.replymate.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import com.replymate.Config

/**
 * Owns the overlay windows: the bubble (spec F4) and the suggestion panel (F5).
 *
 * Must be created with the AccessibilityService as [context] so that
 * TYPE_ACCESSIBILITY_OVERLAY windows are allowed (no SYSTEM_ALERT_WINDOW needed).
 */
class OverlayController(
    private val context: Context,
    private val onBubbleTap: () -> Unit,
    private val onBubbleLongPress: () -> Unit,
    private val onPositionChanged: (BubblePosition) -> Unit,
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val windowSize = ((Config.BUBBLE_SIZE_DP + 2 * Config.BUBBLE_SHADOW_PAD_DP) * density).toInt()

    private var bubble: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var hiding = false

    /** Last saved position; updated from DataStore by the service. */
    var position = BubblePosition(Side.RIGHT, Config.BUBBLE_DEFAULT_Y_FRACTION)
        set(value) {
            field = value
            if (bubble != null && !hiding) applyPosition()
        }

    val isBubbleShown: Boolean get() = bubble != null && !hiding

    fun showBubble() {
        val existing = bubble
        if (existing != null) {
            if (hiding) {
                // Reverse an in-progress fade-out.
                hiding = false
                existing.animate().cancel()
                existing.animate().alpha(1f).setDuration(Config.BUBBLE_FADE_MS).start()
            }
            return
        }
        val view = BubbleView(
            context,
            onTap = onBubbleTap,
            onLongPress = onBubbleLongPress,
            onDrag = ::dragBy,
            onDragEnd = ::snapToEdge,
        )
        val lp = WindowManager.LayoutParams(
            windowSize,
            windowSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        params = lp
        bubble = view
        applyPosition()
        view.alpha = 0f
        try {
            wm.addView(view, lp)
        } catch (e: Exception) {
            bubble = null
            params = null
            return
        }
        view.animate().alpha(1f).setDuration(Config.BUBBLE_FADE_MS).start()
    }

    fun hideBubble() {
        val view = bubble ?: return
        if (hiding) return
        hiding = true
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(Config.BUBBLE_FADE_MS).withEndAction {
            if (hiding) removeBubbleNow()
        }.start()
    }

    /** Immediate removal, e.g. when the service is shutting down. */
    fun destroy() {
        closePanel()
        bubble?.animate()?.cancel()
        removeBubbleNow()
    }

    // --- Suggestion panel (spec F5) ---

    private var panel: SuggestionPanelView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    val isPanelOpen: Boolean get() = panel != null

    /**
     * Opens the panel anchored to the bottom, [bottomOffset] px up (keyboard or
     * navigation bar). Touchable but NOT_FOCUSABLE so the chat app keeps the
     * keyboard; WATCH_OUTSIDE_TOUCH so a tap elsewhere closes it.
     */
    fun openPanel(callbacks: SuggestionPanelView.Callbacks, bottomOffset: Int): SuggestionPanelView {
        panel?.let { return it }
        val view = SuggestionPanelView(context, callbacks)
        view.maxPanelHeight = (screenSize().y * Config.PANEL_MAX_HEIGHT_RATIO).toInt()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = bottomOffset
        }
        try {
            wm.addView(view, lp)
        } catch (e: Exception) {
            throw IllegalStateException("Couldn't open panel: ${e.message}", e)
        }
        panel = view
        panelParams = lp
        view.slideIn()
        return view
    }

    fun renderPanel(state: PanelState) {
        panel?.render(state)
    }

    /** Keeps the panel just above the keyboard when it opens or closes. */
    fun setPanelBottomOffset(px: Int) {
        val view = panel ?: return
        val lp = panelParams ?: return
        if (lp.y == px) return
        lp.y = px
        if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, lp) }
    }

    /**
     * While ReplyMate scrolls the chat, the panel must let touches through so
     * the scroll swipe reaches the chat underneath, and must NOT treat that
     * swipe as an "outside tap" (which would close the panel and cancel the read).
     */
    fun setPanelTouchThrough(through: Boolean) {
        val view = panel ?: return
        val lp = panelParams ?: return
        val flags = panelFlags(lp.flags, through)
        if (flags == lp.flags) return
        lp.flags = flags
        if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, lp) }
    }

    fun closePanel() {
        val view = panel ?: return
        runCatching { wm.removeViewImmediate(view) }
        panel = null
        panelParams = null
    }

    /** Call after rotation so the bubble stays on its edge. */
    fun onScreenChanged() {
        if (bubble != null && !hiding) applyPosition()
    }

    private fun removeBubbleNow() {
        val view = bubble ?: return
        runCatching { wm.removeViewImmediate(view) }
        bubble = null
        params = null
        hiding = false
    }

    private fun applyPosition() {
        val lp = params ?: return
        val screen = screenSize()
        lp.x = BubbleGeometry.xFor(position.side, screen.x, windowSize)
        lp.y = BubbleGeometry.yFor(position.yFraction, screen.y, windowSize)
        update(lp)
    }

    private fun dragBy(dx: Int, dy: Int) {
        val lp = params ?: return
        val screen = screenSize()
        lp.x = (lp.x + dx).coerceIn(0, (screen.x - windowSize).coerceAtLeast(0))
        lp.y = BubbleGeometry.clampY(lp.y + dy, screen.y, windowSize)
        update(lp)
    }

    private fun snapToEdge() {
        val lp = params ?: return
        val screen = screenSize()
        val side = BubbleGeometry.snapSide(lp.x + windowSize / 2, screen.x)
        val newPos = BubblePosition(side, BubbleGeometry.fractionFor(lp.y, screen.y))
        position = newPos // re-applies x/y
        onPositionChanged(newPos)
    }

    private fun update(lp: WindowManager.LayoutParams) {
        val view = bubble ?: return
        if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, lp) }
    }

    fun screenSize(): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(it)
            }
        }

    fun screenHeight(): Int = screenSize().y

    companion object {
        /**
         * Normal: touchable + watch outside taps (tap outside closes the panel).
         * Touch-through (while scrolling): not touchable and not watching outside.
         */
        fun panelFlags(current: Int, through: Boolean): Int {
            val touchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            val watch = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            return if (through) (current or touchable) and watch.inv() else (current and touchable.inv()) or watch
        }
    }
}
