package com.replymate

/**
 * Every tunable number in the app lives here (spec section 16).
 * Later milestones add the reader, overlay and Gemini constants.
 */
object Config {
    /** Packages ReplyMate works with (WhatsApp, WhatsApp Business, Messenger). */
    val SUPPORTED_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca")

    // --- Persona (spec F8) ---
    /** Verified stable at ai.google.dev on 2026-11-18. Editable in the app. */
    const val DEFAULT_MODEL_ID = "gemini-3.5-flash-lite"
    const val ABOUT_ME_MAX_CHARS = 500
    const val MAX_EXAMPLE_LINES = 40
    /** Fewest example messages for the Setup checklist to tick "Persona". */
    const val MIN_EXAMPLES_FOR_READY = 5
    /** What the UI recommends. */
    const val RECOMMENDED_EXAMPLES = "15–20"

    /** Delay before persona edits are written to storage. */
    const val AUTOSAVE_DEBOUNCE_MS = 400L
}
