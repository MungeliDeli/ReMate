package com.replymate.ai

/**
 * Per-reply style choices picked in the panel before asking Gemini.
 * They shape this one reply; the persona (who the owner is) stays the same.
 */
enum class Mood(val label: String, val instruction: String) {
    LIGHT("Light", "light-hearted and easy-going"),
    FLIRTY("Flirty", "flirty and charming: confident, playful tension and compliments; respectful, never crude or sexual"),
    ROMANTIC("Romantic", "romantic and affectionate"),
    SENTIMENTAL("Sentimental", "sentimental and heartfelt, showing genuine feeling"),
    COMFORTING("Comforting", "comforting and supportive, for when they are sad or going through something; kind, no jokes at their expense"),
}

enum class ReplyLength(val label: String, val instruction: String) {
    SHORT("Short", "very short: a few words, at most one short sentence"),
    MEDIUM("Medium", "1-2 sentences"),
    LONG("Long", "3-4 sentences, still natural for a text message"),
}

enum class Vibe(val label: String, val instruction: String) {
    CHILLED("Chilled", "relaxed and laid-back"),
    FUNNY("Funny", "funny, with real humour or a joke"),
    PLAYFUL("Playful", "playful and teasing"),
    SERIOUS("Serious", "sincere and serious, no jokes"),
}

data class ReplyOptions(
    /** Up to [MAX_MOODS], in the order picked. */
    val moods: List<Mood> = emptyList(),
    val length: ReplyLength? = null,
    val vibe: Vibe? = null,
) {
    /** Nothing picked: the default casual / warm / short trio. */
    val isAuto: Boolean get() = moods.isEmpty() && length == null && vibe == null

    /** Picking a third mood drops the oldest one. */
    fun toggle(mood: Mood): ReplyOptions =
        if (mood in moods) copy(moods = moods - mood)
        else copy(moods = (moods + mood).takeLast(MAX_MOODS))

    /** Tapping the selected length/vibe again clears it. */
    fun toggle(l: ReplyLength): ReplyOptions = copy(length = if (length == l) null else l)
    fun toggle(v: Vibe): ReplyOptions = copy(vibe = if (vibe == v) null else v)

    /** e.g. "Flirty + Light · Short · Playful", or "Auto". */
    fun summary(): String {
        if (isAuto) return "Auto"
        return listOfNotNull(
            moods.takeIf { it.isNotEmpty() }?.joinToString(" + ") { it.label },
            length?.label,
            vibe?.label,
        ).joinToString(" · ")
    }

    companion object {
        const val MAX_MOODS = 2
    }
}
