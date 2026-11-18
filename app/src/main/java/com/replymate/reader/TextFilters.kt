package com.replymate.reader

/** Recognises text that is on screen but isn't a chat message (spec F3). */
object TextFilters {

    private val timeOnly = Regex("""^\d{1,2}[:.]\d{2}(\s?[AaPp]\.?\s?[Mm]\.?)?$""")
    private val numericDate = Regex("""^\d{1,4}[/.\-]\d{1,2}[/.\-]\d{1,4}$""")
    private val months = "january|february|march|april|may|june|july|august|september|october|november|december|" +
        "jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec"
    private val weekdays = "monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun"
    // "18 November 2026", "November 18, 2026", "Nov 18", "Wed, 18 Nov"
    private val wordDate = Regex(
        """^(($weekdays),?\s+)?(\d{1,2}\s+($months)|($months)\s+\d{1,2})(,?\s+\d{4})?(\s+(at\s+)?\d{1,2}[:.]\d{2}(\s?[ap]m)?)?$""",
        RegexOption.IGNORE_CASE,
    )
    // "Mon 14:05", "Yesterday at 9:41 PM", "Today"
    private val dayLabel = Regex(
        """^(today|yesterday|$weekdays)(,?\s+(at\s+)?\d{1,2}[:.]\d{2}(\s?[ap]m)?)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val unreadBanner = Regex("""^\d+\s+unread\s+messages?$""", RegexOption.IGNORE_CASE)

    private val exactNoise = setOf(
        "seen", "delivered", "sent", "sending", "sending…", "sending...", "read", "edited",
        "typing…", "typing...", "online", "active now", "message", "type a message",
        "you can now message and call each other", "tap for more info",
    )

    private val noisePrefixes = listOf(
        "messages and calls are end-to-end encrypted",
        "messages are end-to-end encrypted",
        "this chat is with a business account",
        "you created group", "you joined using",
        "seen by", "seen at", "delivered at", "active ", "last seen",
        "missed voice call", "missed video call", "voice call", "video call",
        "this message was deleted", "you deleted this message",
        "disappearing messages",
    )

    fun isNoise(raw: String): Boolean {
        val s = raw.trim()
        if (s.isEmpty()) return true
        val lower = s.lowercase()
        return lower in exactNoise ||
            noisePrefixes.any { lower.startsWith(it) } ||
            timeOnly.matches(s) ||
            numericDate.matches(s) ||
            wordDate.matches(s) ||
            dayLabel.matches(s) ||
            unreadBanner.matches(s)
    }
}
