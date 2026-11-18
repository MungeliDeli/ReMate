package com.replymate.reader

import android.view.accessibility.AccessibilityNodeInfo
import com.replymate.Config

/** Screen rectangle in pixels (plain Kotlin so readers can be unit-tested). */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
}

/**
 * A frozen copy of one accessibility node and its children. Readers work on
 * this instead of live AccessibilityNodeInfo objects, so they can't hit stale
 * nodes halfway through, and tests can build trees from recorded dumps.
 */
data class ScreenNode(
    val packageName: String? = null,
    val className: String? = null,
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val bounds: Box = Box(0, 0, 0, 0),
    val editable: Boolean = false,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val visible: Boolean = true,
    val children: List<ScreenNode> = emptyList(),
) {
    /** View ID without the "package:id/" prefix. */
    val shortId: String? get() = viewId?.substringAfter(":id/", viewId)

    val simpleClass: String get() = className?.substringAfterLast('.') ?: ""

    /** This node and all descendants, depth-first in screen-tree order. */
    fun walk(): Sequence<ScreenNode> = sequence {
        yield(this@ScreenNode)
        for (c in children) yieldAll(c.walk())
    }

    fun count(): Int = 1 + children.sumOf { it.count() }
}

object ScreenSnapshot {
    /** Copies the live tree (at most [Config.MAX_NODES_SCAN] nodes). */
    fun from(root: AccessibilityNodeInfo): ScreenNode {
        val budget = intArrayOf(Config.MAX_NODES_SCAN)
        return copy(root, budget) ?: ScreenNode()
    }

    private fun copy(node: AccessibilityNodeInfo, budget: IntArray): ScreenNode? {
        if (budget[0] <= 0) return null
        budget[0]--
        return try {
            val r = NodeUtils.bounds(node)
            val kids = ArrayList<ScreenNode>()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                copy(child, budget)?.let(kids::add)
                NodeUtils.recycle(child)
            }
            ScreenNode(
                packageName = node.packageName?.toString(),
                className = node.className?.toString(),
                viewId = node.viewIdResourceName,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                bounds = Box(r.left, r.top, r.right, r.bottom),
                editable = node.isEditable,
                clickable = node.isClickable,
                scrollable = node.isScrollable,
                visible = node.isVisibleToUser,
                children = kids,
            )
        } catch (_: Exception) {
            null // stale node: drop it
        }
    }
}
