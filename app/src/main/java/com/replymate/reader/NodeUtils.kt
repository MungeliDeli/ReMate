package com.replymate.reader

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.replymate.Config

/**
 * Helpers for walking other apps' view trees. Nodes can go stale at any time,
 * so every access is wrapped and failures simply mean "not found".
 */
object NodeUtils {

    /** Releases a node on Android < 13; a no-op (and deprecated) from 13 on. */
    fun recycle(node: AccessibilityNodeInfo?) {
        if (node != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            runCatching { node.recycle() }
        }
    }

    fun bounds(node: AccessibilityNodeInfo): Rect = Rect().also { node.getBoundsInScreen(it) }

    /**
     * The chat composer (message input box), or null.
     * WhatsApp: by view ID `<pkg>:id/entry`. Otherwise / as fallback: the lowest
     * visible editable node whose top is below [Config.COMPOSER_MIN_TOP_RATIO] of the
     * screen. This skips search bars at the top of chat lists.
     * The caller must [recycle] the returned node.
     */
    fun findComposer(root: AccessibilityNodeInfo, screenHeight: Int): AccessibilityNodeInfo? = try {
        findComposerById(root) ?: findLowestEditable(root, screenHeight)
    } catch (e: Exception) {
        null
    }

    private fun findComposerById(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val pkg = root.packageName?.toString() ?: return null
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return null
        val matches = root.findAccessibilityNodeInfosByViewId("$pkg:id/${Config.WHATSAPP_COMPOSER_ID}")
        val hit = matches.firstOrNull { runCatching { it.isVisibleToUser }.getOrDefault(false) }
        matches.filter { it !== hit }.forEach(::recycle)
        return hit
    }

    private fun findLowestEditable(root: AccessibilityNodeInfo, screenHeight: Int): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestTop = -1
        var visited = 0
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        // Push children of root (never recycle the root here; the caller owns it).
        for (i in 0 until root.childCount) root.getChild(i)?.let(stack::addLast)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            visited++
            var keep = false
            try {
                if (visited <= Config.MAX_NODES_SCAN) {
                    if (node.isEditable && node.isVisibleToUser) {
                        val top = bounds(node).top
                        if (isComposerPosition(top, screenHeight) && top > bestTop) {
                            recycle(best)
                            best = node
                            bestTop = top
                            keep = true
                        }
                    }
                    for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
                }
            } catch (_: Exception) {
                // Stale node: skip it.
            }
            if (!keep) recycle(node)
        }
        return best
    }

    /** Composers sit in the lower part of the screen; search bars sit at the top. */
    fun isComposerPosition(top: Int, screenHeight: Int): Boolean =
        screenHeight > 0 && top >= screenHeight * Config.COMPOSER_MIN_TOP_RATIO
}
