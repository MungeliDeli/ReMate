package com.replymate.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.replymate.R
import com.replymate.ai.Suggestion

/**
 * Bottom-sheet suggestion panel (spec F5 / section 7), built with plain Views
 * because Compose needs extra lifecycle plumbing inside a service window.
 */
@SuppressLint("ViewConstructor")
class SuggestionPanelView(
    context: Context,
    private val callbacks: Callbacks,
) : FrameLayout(context) {

    interface Callbacks {
        fun onPick(suggestion: Suggestion)
        fun onCopy(suggestion: Suggestion)
        fun onRegenerate()
        fun onClose()
        fun onAction(action: PanelAction)
        fun onOpenDebug()
        fun onCapture()
        fun onOutsideTouch()
    }

    private val primary = color(R.color.panel_primary)
    private val textColor = color(R.color.panel_text)
    private val textSecondary = color(R.color.panel_text_secondary)
    private val ripple = ColorStateList.valueOf(color(R.color.panel_ripple))

    private val card = MaxHeightLinearLayout(context)
    private val overflowRow = LinearLayout(context)
    private val content = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val regenerateButton: ImageButton
    private var pulse: ValueAnimator? = null

    var maxPanelHeight: Int
        get() = card.maxHeightPx
        set(value) {
            card.maxHeightPx = value
            card.requestLayout()
        }

    init {
        clipChildren = false
        clipToPadding = false

        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(color(R.color.panel_surface), dp(16))
        card.elevation = dp(8).toFloat()
        card.setPadding(dp(12), dp(4), dp(12), dp(12))
        card.isClickable = true // swallow taps on empty panel areas
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(12), dp(12), dp(12), dp(12))
        })

        // Header: title, overflow, regenerate, close (48dp touch targets).
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = "ReplyMate"
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(iconButton(R.drawable.ic_more_vert, "More options") {
            overflowRow.visibility = if (overflowRow.visibility == VISIBLE) GONE else VISIBLE
        })
        regenerateButton = iconButton(R.drawable.ic_refresh, "Regenerate suggestions") { callbacks.onRegenerate() }
        header.addView(regenerateButton)
        header.addView(iconButton(R.drawable.ic_close, "Close") { callbacks.onClose() })
        card.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))

        // Overflow actions (spec F10: capture node tree from the panel).
        overflowRow.orientation = LinearLayout.HORIZONTAL
        overflowRow.visibility = GONE
        overflowRow.addView(textButton("Capture screen for Debug") {
            overflowRow.visibility = GONE
            callbacks.onCapture()
        })
        overflowRow.addView(textButton("Open debug") { callbacks.onOpenDebug() })
        card.addView(overflowRow)

        content.orientation = LinearLayout.VERTICAL
        scroll.isVerticalScrollBarEnabled = true
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        card.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Spec F5: tapping outside the panel closes it. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            callbacks.onOutsideTouch()
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    /** Panel slide-up, 200 ms, decelerate (spec section 7). */
    fun slideIn() {
        card.alpha = 0f
        card.post {
            card.translationY = card.height.toFloat() + dp(12)
            card.alpha = 1f
            card.animate().translationY(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    fun render(state: PanelState) {
        stopPulse()
        content.removeAllViews()
        regenerateButton.isEnabled = state != PanelState.Loading
        regenerateButton.alpha = if (regenerateButton.isEnabled) 1f else 0.4f
        when (state) {
            PanelState.Loading -> renderLoading()
            is PanelState.Results -> {
                state.hint?.let { content.addView(caption(it)) }
                state.suggestions.forEachIndexed { i, s -> content.addView(suggestionCard(s), cardParams(i)) }
            }
            is PanelState.Error -> {
                content.addView(message(state.message, color(R.color.panel_error)))
                state.action?.let { a -> content.addView(textButton(a.label) { callbacks.onAction(a) }) }
            }
            PanelState.Empty -> {
                content.addView(message("Couldn't read this chat", textColor))
                content.addView(textButton("Open debug") { callbacks.onOpenDebug() })
            }
        }
        scroll.scrollTo(0, 0)
    }

    private fun renderLoading() {
        content.addView(caption("Thinking…"))
        val placeholders = (0 until 3).map { i ->
            View(context).apply { background = rounded(color(R.color.panel_placeholder), dp(12)) }
                .also { content.addView(it, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(64)).apply { if (i > 0) topMargin = dp(8) }) }
        }
        pulse = ValueAnimator.ofFloat(1f, 0.45f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { a -> placeholders.forEach { it.alpha = a.animatedValue as Float } }
            start()
        }
    }

    private fun suggestionCard(s: Suggestion): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val shape = rounded(color(R.color.panel_card), dp(12)).apply { setStroke(dp(1), color(R.color.panel_outline)) }
        background = RippleDrawable(ripple, shape, null)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        isClickable = true
        isLongClickable = true
        contentDescription = "${s.tone} suggestion: ${s.text}. Tap to insert, long-press to copy."
        setOnClickListener { callbacks.onPick(s) }
        setOnLongClickListener { callbacks.onCopy(s); true }

        addView(TextView(context).apply {
            text = s.tone.uppercase()
            setTextColor(primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.06f
            background = rounded(color(R.color.panel_chip_bg), dp(6))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        addView(TextView(context).apply {
            text = s.text
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lineHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics).toInt()
            }
            maxLines = 6
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
    }

    private fun cardParams(index: Int) =
        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            if (index > 0) topMargin = dp(8)
        }

    private fun caption(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(textSecondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(2), 0, 0, dp(8))
    }

    private fun message(text: String, color: Int) = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(2), dp(4), dp(2), dp(4))
    }

    private fun textButton(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(primary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(48)
        setPadding(dp(8), 0, dp(12), 0)
        background = RippleDrawable(ripple, null, rounded(0xFFFFFFFF.toInt(), dp(8)))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun iconButton(icon: Int, description: String, onClick: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(textSecondary)
        background = RippleDrawable(ripple, null, null)
        contentDescription = description
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setOnClickListener { onClick() }
    }

    private fun rounded(fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }

    private fun color(id: Int) = context.getColor(id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** A LinearLayout that never grows taller than [maxHeightPx] (spec: max 60% of screen). */
    private class MaxHeightLinearLayout(context: Context) : LinearLayout(context) {
        var maxHeightPx = Int.MAX_VALUE

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val spec = if (maxHeightPx == Int.MAX_VALUE) heightMeasureSpec else {
                val parentMax = MeasureSpec.getSize(heightMeasureSpec)
                val limit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) maxHeightPx else minOf(maxHeightPx, parentMax)
                MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
            }
            super.onMeasure(widthMeasureSpec, spec)
        }
    }

}
