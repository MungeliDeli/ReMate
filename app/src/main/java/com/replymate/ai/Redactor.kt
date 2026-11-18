package com.replymate.ai

/**
 * Removes personal data from text before it leaves the phone (spec F7).
 * Order matters: emails before links (an email contains a domain), links
 * before numbers (URLs contain digits), phones before plain digit runs.
 */
object Redactor {
    const val PHONE = "[PHONE]"
    const val EMAIL = "[EMAIL]"
    const val LINK = "[LINK]"
    const val NUMBER = "[NUMBER]"

    private val email = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    private const val TLDS = "com|net|org|io|co|zm|me|app|dev|ly|gl|be|info|biz|edu|gov|tv|uk|za|ke|ng|gh|tz|mw|link|xyz|site|online|shop"
    private val link = Regex(
        """(?i)\b(?:https?://|www\.)\S+|\b[a-z0-9\-]+(?:\.[a-z0-9\-]+)*\.(?:$TLDS)\b(?:/\S*)?""",
    )

    /** Digits with optional leading +, spaces or dashes between them. */
    private val phoneLike = Regex("""(?<![\w+])\+?\d(?:[\d \-]*\d)?(?!\w)""")

    fun redact(text: String): String {
        var out = email.replace(text, EMAIL)
        out = link.replace(out, LINK)
        out = phoneLike.replace(out) { m -> classifyNumber(m.value) }
        return out
    }

    /**
     * 7+ digits (counting across +, spaces, dashes) -> [PHONE], e.g.
     * "+260 97 123 4567", "0971234567", "097-123-4567".
     * A plain run of 6 digits (codes, PINs) -> [NUMBER].
     * Anything shorter is left alone (times, prices, years).
     */
    private fun classifyNumber(match: String): String {
        val trimmed = match.trimEnd(' ', '-')
        val trailing = match.substring(trimmed.length)
        val digits = trimmed.count { it.isDigit() }
        val plain = trimmed.all { it.isDigit() }
        return when {
            digits >= 7 -> PHONE + trailing
            digits >= 6 && plain -> NUMBER + trailing
            else -> match
        }
    }
}
