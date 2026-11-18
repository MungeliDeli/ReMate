package com.replymate.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import com.replymate.Config
import com.replymate.R
import kotlin.math.abs

/**
 * The floating 52dp circle. It reports taps and drags; [OverlayController]
 * moves the window.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class BubbleView(
    context: Context,
    private val onTap: () -> Unit,
    private val onDrag: (dx: Int, dy: Int) -> Unit,
    private val onDragEnd: () -> Unit,
) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val circle = ImageView(context).apply {
        setImageResource(R.drawable.ic_bubble)
        setBackgroundResource(R.drawable.bubble_background)
        scaleType = ImageView.ScaleType.CENTER
        elevation = dp(Config.BUBBLE_ELEVATION_DP)
        alpha = Config.BUBBLE_IDLE_ALPHA
        contentDescription = context.getString(R.string.bubble_content_description)
        isClickable = true
        isFocusable = false
    }

    init {
        val size = dp(Config.BUBBLE_SIZE_DP.toFloat()).toInt()
        addView(circle, LayoutParams(size, size, Gravity.CENTER))
        clipChildren = false
        clipToPadding = false

        circle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    lastX = e.rawX; lastY = e.rawY
                    dragging = false
                    circle.alpha = 1f
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && (abs(e.rawX - downX) > touchSlop || abs(e.rawY - downY) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        onDrag((e.rawX - lastX).toInt(), (e.rawY - lastY).toInt())
                        lastX = e.rawX; lastY = e.rawY
                    }
                }
                MotionEvent.ACTION_UP -> {
                    circle.alpha = Config.BUBBLE_IDLE_ALPHA
                    if (dragging) {
                        onDragEnd()
                    } else {
                        circle.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        circle.performClick()
                        onTap()
                    }
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    circle.alpha = Config.BUBBLE_IDLE_ALPHA
                    if (dragging) onDragEnd()
                    dragging = false
                }
            }
            true
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
