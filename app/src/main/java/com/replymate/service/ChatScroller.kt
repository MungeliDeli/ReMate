package com.replymate.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.replymate.Config
import com.replymate.reader.NodeUtils
import com.replymate.reader.SwipePlan
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Scrolls the open chat one page up or down so more history can be read.
 * Only ever scrolls: prefers the list's own scroll action (WhatsApp) and falls
 * back to a straight vertical swipe in the message area (Messenger exposes no
 * scrollable list). Never taps; HardRulesTest checks this file.
 */
class ChatScroller(private val service: AccessibilityService) {

    enum class Method { NODE, SWIPE }

    /**
     * One page towards older ([older] = true) or newer messages. Returns how it
     * scrolled, or null if it couldn't (e.g. already at the top).
     */
    suspend fun scroll(root: AccessibilityNodeInfo, older: Boolean, screenWidth: Int, screenHeight: Int, prefer: Method? = null): Method? {
        if (prefer != Method.SWIPE) {
            val list = findScrollableList(root, older)
            if (list != null) {
                val ok = try {
                    if (older) list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    else list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                } catch (_: Exception) {
                    false
                } finally {
                    NodeUtils.recycle(list)
                }
                return if (ok) Method.NODE else null
            }
            if (prefer == Method.NODE) return null
        }
        val composer = NodeUtils.findComposer(root, screenHeight)
        val composerTop = composer?.let { NodeUtils.bounds(it).top }
        NodeUtils.recycle(composer)
        val swipe = SwipePlan.forArea(SwipePlan.listArea(screenWidth, screenHeight, composerTop), older) ?: return null
        return if (swipe(swipe)) Method.SWIPE else null
    }

    /** Largest visible node that offers the scroll action we need. Caller recycles it. */
    private fun findScrollableList(root: AccessibilityNodeInfo, older: Boolean): AccessibilityNodeInfo? {
        val wanted = if (older) AccessibilityAction.ACTION_SCROLL_BACKWARD else AccessibilityAction.ACTION_SCROLL_FORWARD
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let(stack::addLast)
        var visited = 0
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            var keep = false
            try {
                if (++visited <= Config.MAX_NODES_SCAN) {
                    if (node.isVisibleToUser && !node.isEditable && node.actionList.any { it.id == wanted.id }) {
                        val b = NodeUtils.bounds(node)
                        val area = b.width().toLong() * b.height()
                        if (area > bestArea) {
                            NodeUtils.recycle(best)
                            best = node
                            bestArea = area
                            keep = true
                        }
                    }
                    for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
                }
            } catch (_: Exception) {
                // stale node
            }
            if (!keep) NodeUtils.recycle(node)
        }
        return best
    }

    /** Vertical drag, then a short hold at the end so the list stops instead of flinging. */
    private suspend fun swipe(s: SwipePlan.Swipe): Boolean {
        val move = Path().apply {
            moveTo(s.x, s.fromY)
            lineTo(s.x, s.toY)
        }
        val drag = GestureDescription.StrokeDescription(move, 0, Config.SWIPE_MOVE_MS, true)
        if (!dispatch(GestureDescription.Builder().addStroke(drag).build())) return false
        val stay = Path().apply {
            moveTo(s.x, s.toY)
            lineTo(s.x, s.toY + 1f)
        }
        val hold = drag.continueStroke(stay, 0, Config.SWIPE_HOLD_MS, false)
        return dispatch(GestureDescription.Builder().addStroke(hold).build())
    }

    private suspend fun dispatch(gesture: GestureDescription): Boolean = suspendCancellableCoroutine { cont ->
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(false)
            }
        }
        val accepted = try {
            service.dispatchGesture(gesture, callback, null)
        } catch (_: Exception) {
            false
        }
        if (!accepted && cont.isActive) cont.resume(false)
    }
}
