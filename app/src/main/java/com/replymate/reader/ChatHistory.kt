package com.replymate.reader

/**
 * Joins messages read from several screens of the same chat (spec roadmap
 * "longer context"). Consecutive screens usually overlap a little; the overlap
 * is found and kept once.
 */
object ChatHistory {

    /**
     * [pages] are ordered oldest screen first; each page is oldest message first.
     * Keeps the newest [max] messages.
     */
    fun merge(pages: List<List<ChatMessage>>, max: Int): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        for (page in pages) {
            val overlap = overlap(out, page)
            out.addAll(page.drop(overlap))
        }
        return out.takeLast(max)
    }

    /** Longest k where the last k of [older] equal the first k of [newer]. */
    fun overlap(older: List<ChatMessage>, newer: List<ChatMessage>): Int {
        for (k in minOf(older.size, newer.size) downTo 1) {
            if (older.subList(older.size - k, older.size) == newer.subList(0, k)) return k
        }
        return 0
    }
}
