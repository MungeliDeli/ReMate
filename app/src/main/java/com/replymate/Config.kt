package com.replymate

/**
 * Every tunable number in the app lives here (spec section 16).
 * Later milestones add the reader, overlay and Gemini constants.
 */
object Config {
    /** Packages ReplyMate works with (WhatsApp, WhatsApp Business, Messenger). */
    val SUPPORTED_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca")
}
