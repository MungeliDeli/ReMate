package com.replymate.reader

import com.replymate.service.SupportedApp

enum class Sender { ME, THEM, UNKNOWN }

data class ChatMessage(val sender: Sender, val text: String)

data class Conversation(
    val app: SupportedApp,
    /** Used only on the phone; replaced before anything is sent to Gemini (spec F7). */
    val contactName: String?,
    /** Oldest -> newest, at most Config.MAX_MESSAGES. */
    val messages: List<ChatMessage>,
) {
    /** Human-readable form for the Debug log. */
    fun describe(): String = buildString {
        append("app=${app.name}  contact=${contactName ?: "(unknown)"}  messages=${messages.size}\n\n")
        for (m in messages) {
            append(
                when (m.sender) {
                    Sender.ME -> "ME:   "
                    Sender.THEM -> "THEM: "
                    Sender.UNKNOWN -> "??:   "
                },
            )
            append(m.text.replace("\n", " ⏎ ")).append('\n')
        }
    }
}
