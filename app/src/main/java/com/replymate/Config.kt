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

    // --- Chat detection (spec F1/F2) ---
    /** Wait this long after the last accessibility event before re-checking the screen. */
    const val DETECT_DEBOUNCE_MS = 350L
    /** While the bubble shows, check this often that a chat app is still in front. */
    const val FOREGROUND_RECHECK_MS = 1000L
    /** WhatsApp composer view ID (verify with the Debug node dump; may change). */
    const val WHATSAPP_COMPOSER_ID = "entry"
    /** An editable node counts as the composer only if its top is below this fraction of the screen. */
    const val COMPOSER_MIN_TOP_RATIO = 0.25f
    /** Safety cap on how many nodes one tree walk may visit. */
    const val MAX_NODES_SCAN = 3000

    // --- Bubble (spec F4 / section 7) ---
    const val BUBBLE_SIZE_DP = 52
    /** Extra transparent space around the bubble so its shadow isn't clipped. */
    const val BUBBLE_SHADOW_PAD_DP = 6
    const val BUBBLE_ELEVATION_DP = 6f
    const val BUBBLE_IDLE_ALPHA = 0.85f
    const val BUBBLE_FADE_MS = 150L
    /** Default position: right edge, 40% down the screen. */
    const val BUBBLE_DEFAULT_Y_FRACTION = 0.40f
}
