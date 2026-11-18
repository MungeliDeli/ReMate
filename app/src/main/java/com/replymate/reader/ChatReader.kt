package com.replymate.reader

import com.replymate.Config

/** Reads the open chat from a screen snapshot (spec F3). */
interface ChatReader {
    /** Null when no messages could be found. */
    fun readConversation(root: ScreenNode, screenWidth: Int, screenHeight: Int): Conversation?
}

/** Rules shared by both readers (spec F3 "General rules"). */
object ReaderRules {

    /** A visible piece of text plus where it sits on screen. */
    data class Candidate(val text: String, val bounds: Box)

    /**
     * Sorts top-to-bottom, drops non-message noise, assigns senders by position,
     * keeps the newest [Config.MAX_MESSAGES], trims each to [Config.MAX_MESSAGE_CHARS].
     */
    fun toMessages(candidates: List<Candidate>, screenWidth: Int): List<ChatMessage> =
        candidates
            .asSequence()
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() && !TextFilters.isNoise(it.text) }
            .filter { !isCenteredChip(it.bounds, screenWidth) }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            .map { ChatMessage(senderFor(it.bounds, screenWidth), trimText(it.text)) }
            .toList()
            .takeLast(Config.MAX_MESSAGES)

    /** Right side -> ME, left side -> THEM, judged by the bubble's horizontal centre. */
    fun senderFor(b: Box, screenWidth: Int): Sender {
        if (screenWidth <= 0 || b.width <= 0) return Sender.UNKNOWN
        return if (b.centerX >= screenWidth * Config.SENDER_SPLIT_RATIO) Sender.ME else Sender.THEM
    }

    /**
     * Date separators and system notices sit centred with a wide gap on BOTH
     * sides; real messages hug one edge.
     */
    fun isCenteredChip(b: Box, screenWidth: Int): Boolean {
        if (screenWidth <= 0) return false
        val minGap = screenWidth * Config.CENTERED_CHIP_MIN_GAP_RATIO
        val leftGap = b.left
        val rightGap = screenWidth - b.right
        val centred = kotlin.math.abs(leftGap - rightGap) <= screenWidth * Config.CENTERED_CHIP_TOLERANCE_RATIO
        return centred && leftGap >= minGap && rightGap >= minGap
    }

    fun trimText(s: String): String =
        if (s.length <= Config.MAX_MESSAGE_CHARS) s else s.take(Config.MAX_MESSAGE_CHARS).trimEnd() + "…"

    /** First text in the top band of the screen (toolbar), used as the chat title. */
    fun toolbarTitle(root: ScreenNode, screenHeight: Int, exclude: (ScreenNode) -> Boolean = { false }): String? =
        root.walk()
            .filter { it.visible && !it.editable && !exclude(it) }
            .filter { it.bounds.bottom in 1..(screenHeight * Config.TOOLBAR_HEIGHT_RATIO).toInt() }
            .mapNotNull { n -> n.text?.trim()?.takeIf { it.isNotEmpty() && !TextFilters.isNoise(it) } }
            .firstOrNull()

    /** The biggest scrollable container is almost always the message list. */
    fun messageList(root: ScreenNode): ScreenNode? =
        root.walk()
            .filter { it.scrollable && it.visible && !it.editable }
            .maxByOrNull { it.bounds.width.toLong() * it.bounds.height }

    /**
     * Visible text inside [container]. A node with text is taken whole (its
     * children are skipped, to avoid duplicates). A node without text
     * contributes its content description only when nothing below it has text.
     */
    fun collectTexts(container: ScreenNode, skip: (ScreenNode) -> Boolean): List<Candidate> {
        val out = ArrayList<Candidate>()
        fun visit(n: ScreenNode): Boolean { // returns true if it produced any text
            if (!n.visible || n.editable || skip(n)) return false
            val text = n.text?.trim()
            if (!text.isNullOrEmpty()) {
                out += Candidate(text, n.bounds)
                return true
            }
            var found = false
            for (c in n.children) found = visit(c) || found
            if (!found && n !== container) {
                val desc = n.contentDescription?.trim()
                if (!desc.isNullOrEmpty() && isTextual(n)) {
                    out += Candidate(desc, n.bounds)
                    found = true
                }
            }
            return found
        }
        visit(container)
        return out
    }

    /** Buttons and images carry descriptions like "Like" or "Photo"; those aren't messages. */
    private fun isTextual(n: ScreenNode): Boolean {
        val c = n.simpleClass
        return !c.contains("Button") && !c.contains("Image")
    }
}
