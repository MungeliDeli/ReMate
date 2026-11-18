package com.replymate.debug

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.replymate.Config
import com.replymate.reader.NodeUtils

/** One node of a captured tree, flattened for printing. */
data class DumpLine(
    val depth: Int,
    val className: String?,
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val bounds: Rect?,
    val editable: Boolean,
    val clickable: Boolean,
    val scrollable: Boolean,
)

/**
 * Captures a window's accessibility node tree as plain text (spec F10) so the
 * owner can share it with Claude Code for reader tuning.
 */
object NodeDump {
    const val TEXT_LIMIT = 60

    fun capture(root: AccessibilityNodeInfo): List<DumpLine> {
        val lines = ArrayList<DumpLine>()
        walk(root, 0, lines)
        return lines
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, out: MutableList<DumpLine>) {
        if (out.size >= Config.MAX_NODES_SCAN) return
        try {
            out += DumpLine(
                depth = depth,
                className = node.className?.toString(),
                viewId = node.viewIdResourceName,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                bounds = NodeUtils.bounds(node),
                editable = node.isEditable,
                clickable = node.isClickable,
                scrollable = node.isScrollable,
            )
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, depth + 1, out)
                NodeUtils.recycle(child)
            }
        } catch (_: Exception) {
            // Stale node: keep what we have.
        }
    }

    /** Pure formatting, unit-tested. One indented line per node. */
    fun format(lines: List<DumpLine>): String = buildString {
        for (l in lines) {
            append("  ".repeat(l.depth))
            append(l.className?.substringAfterLast('.') ?: "?")
            l.viewId?.let { append(" id=").append(it.substringAfter(":id/", it)) }
            l.text?.takeIf { it.isNotEmpty() }?.let { append(" text=\"").append(clip(it)).append('"') }
            l.contentDescription?.takeIf { it.isNotEmpty() }?.let { append(" desc=\"").append(clip(it)).append('"') }
            l.bounds?.let { append(" [${it.left},${it.top}][${it.right},${it.bottom}]") }
            if (l.editable) append(" EDITABLE")
            if (l.clickable) append(" clickable")
            if (l.scrollable) append(" scrollable")
            append('\n')
        }
        if (lines.size >= Config.MAX_NODES_SCAN) append("… stopped at ${Config.MAX_NODES_SCAN} nodes\n")
    }

    /** Single line, max [TEXT_LIMIT] chars, quotes escaped. */
    fun clip(s: String): String {
        val one = s.replace("\r", "").replace("\n", "⏎").replace("\"", "'")
        return if (one.length <= TEXT_LIMIT) one else one.take(TEXT_LIMIT) + "…"
    }
}
