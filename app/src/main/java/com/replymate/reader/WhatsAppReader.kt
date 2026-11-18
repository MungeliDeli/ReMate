package com.replymate.reader

import com.replymate.Config
import com.replymate.service.SupportedApp

/**
 * WhatsApp / WhatsApp Business (spec F3). Uses the message_text view IDs when
 * present; otherwise falls back to the text inside the message list.
 * IDs can change between WhatsApp versions: verify with a Debug node dump.
 */
object WhatsAppReader : ChatReader {

    private val skipIds = setOf(
        "conversation_row_date_divider", "date", "status", "unread_divider_tv", "e2e_notice",
    )

    override fun readConversation(root: ScreenNode, screenWidth: Int, screenHeight: Int): Conversation? {
        val byId = root.walk()
            .filter { it.visible && it.shortId == Config.WHATSAPP_MESSAGE_TEXT_ID }
            .mapNotNull { n -> n.text?.let { ReaderRules.Candidate(it, n.bounds) } }
            .toList()

        val candidates = byId.ifEmpty {
            val list = ReaderRules.messageList(root) ?: return null
            ReaderRules.collectTexts(list) { it.shortId in skipIds }
        }

        val messages = ReaderRules.toMessages(candidates, screenWidth)
        if (messages.isEmpty()) return null

        val title = root.walk()
            .firstOrNull { it.shortId == Config.WHATSAPP_CONTACT_NAME_ID }
            ?.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: ReaderRules.toolbarTitle(root, screenHeight)

        return Conversation(SupportedApp.WHATSAPP, title, messages)
    }
}
