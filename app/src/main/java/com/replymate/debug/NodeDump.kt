package com.replymate.debug

import com.replymate.Config
import com.replymate.reader.ScreenNode

/**
 * Formats a captured screen as plain text (spec F10) so the owner can share it
 * with Claude Code for reader tuning. The test suite can parse this format
 * back into a [ScreenNode] tree, so shared dumps double as test fixtures.
 */
object NodeDump {
    const val TEXT_LIMIT = 60

    /** One indented line per node. */
    fun format(root: ScreenNode): String = buildString {
        appendNode(root, 0)
        if (root.count() >= Config.MAX_NODES_SCAN) append("… stopped at ${Config.MAX_NODES_SCAN} nodes\n")
    }

    private fun StringBuilder.appendNode(n: ScreenNode, depth: Int) {
        append("  ".repeat(depth))
        append(n.simpleClass.ifEmpty { "?" })
        n.shortId?.let { append(" id=").append(it) }
        n.text?.takeIf { it.isNotEmpty() }?.let { append(" text=\"").append(clip(it)).append('"') }
        n.contentDescription?.takeIf { it.isNotEmpty() }?.let { append(" desc=\"").append(clip(it)).append('"') }
        with(n.bounds) { append(" [$left,$top][$right,$bottom]") }
        if (n.editable) append(" EDITABLE")
        if (n.clickable) append(" clickable")
        if (n.scrollable) append(" scrollable")
        if (!n.visible) append(" hidden")
        append('\n')
        for (c in n.children) appendNode(c, depth + 1)
    }

    /** Single line, max [TEXT_LIMIT] chars, quotes swapped so the dump stays parseable. */
    fun clip(s: String): String {
        val one = s.replace("\r", "").replace("\n", "⏎").replace("\"", "'")
        return if (one.length <= TEXT_LIMIT) one else one.take(TEXT_LIMIT) + "…"
    }
}
