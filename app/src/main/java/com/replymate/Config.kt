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

    // --- Gemini (spec F9) ---
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"
    const val GEMINI_CONNECT_TIMEOUT_S = 10L
    const val GEMINI_READ_TIMEOUT_S = 20L
    const val GEMINI_RETRY_DELAY_MS = 2_000L
    const val GEMINI_TEMPERATURE = 0.9
    const val GEMINI_MAX_OUTPUT_TOKENS = 400
    /** Used if the model rejects MINIMAL thinking and LOW is used instead (thinking tokens count as output). */
    const val GEMINI_MAX_OUTPUT_TOKENS_WITH_THINKING = 1024
    const val SUGGESTION_COUNT = 3

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

    // --- Chat reading (spec F3) ---
    const val MAX_MESSAGES = 15
    const val MAX_MESSAGE_CHARS = 500
    /**
     * A message whose horizontal centre is at or right of this fraction of the
     * screen width is from ME. The spec suggests 0.45; 0.5 is used because long
     * incoming messages can stretch past 45% and be mislabelled. Tune with dumps.
     */
    const val SENDER_SPLIT_RATIO = 0.5f
    /** Text centred with at least this gap on both sides is a date/system chip, not a message. */
    const val CENTERED_CHIP_MIN_GAP_RATIO = 0.15f
    /** How close the two side gaps must be to count as "centred". */
    const val CENTERED_CHIP_TOLERANCE_RATIO = 0.08f
    /** Chat title is looked for in the top 12% of the screen. */
    const val TOOLBAR_HEIGHT_RATIO = 0.12f
    const val WHATSAPP_MESSAGE_TEXT_ID = "message_text"
    const val WHATSAPP_CONTACT_NAME_ID = "conversation_contact_name"
    /** Delivery-ticks image; WhatsApp only shows it on the owner's own messages. */
    const val WHATSAPP_STATUS_ID = "status"

    // --- Bubble (spec F4 / section 7) ---
    const val BUBBLE_SIZE_DP = 52
    /** Extra transparent space around the bubble so its shadow isn't clipped. */
    const val BUBBLE_SHADOW_PAD_DP = 6
    const val BUBBLE_ELEVATION_DP = 6f
    const val BUBBLE_IDLE_ALPHA = 0.85f
    const val BUBBLE_FADE_MS = 150L
    /** Default position: right edge, 40% down the screen. */
    const val BUBBLE_DEFAULT_Y_FRACTION = 0.40f

    // --- Suggestion panel (spec F5) ---
    const val PANEL_MAX_HEIGHT_RATIO = 0.60f
    /** A bubble tap this soon after an outside-tap closed the panel is the same tap: don't reopen. */
    const val PANEL_REOPEN_GUARD_MS = 500L
}
