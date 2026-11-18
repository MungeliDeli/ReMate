package com.replymate.reader

import com.replymate.Config
import com.replymate.service.SupportedApp

/**
 * WhatsApp / WhatsApp Business (spec F3). Uses the message_text view IDs when
 * present; otherwise falls back to the text inside the message list.
 * IDs can change between WhatsApp versions: verify with a Debug node dump.
 *
 * Tuned on a real dump (WhatsApp, Pixel 6, Nov 2026):
 * - each message is a `conversation_row_*` row holding `message_text`, `date`
 *   and, only on outgoing messages, a `status` (delivery ticks) image;
 * - the time sits to the RIGHT of outgoing text, so a medium outgoing message
 *   can look centred: the centred-chip filter must not be used on these rows.
 */
object WhatsAppReader : ChatReader {

    private val skipIds = setOf(
        "conversation_row_date_divider", "date", "status", "unread_divider_tv", "e2e_notice",
        "quoted_title", "quoted_text", "edit_label",
    )

    override fun readConversation(root: ScreenNode, screenWidth: Int, screenHeight: Int): Conversation? {
        val byId = ArrayList<ReaderRules.Candidate>()
        collectById(root, byId)

        val messages = if (byId.isNotEmpty()) {
            ReaderRules.toMessages(byId, screenWidth, skipCenteredChips = false)
        } else {
            val list = ReaderRules.messageList(root) ?: return null
            ReaderRules.toMessages(ReaderRules.collectTexts(list) { it.shortId in skipIds }, screenWidth)
        }
        if (messages.isEmpty()) return null

        val title = root.walk()
            .firstOrNull { it.shortId == Config.WHATSAPP_CONTACT_NAME_ID }
            ?.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: ReaderRules.toolbarTitle(root, screenHeight)

        return Conversation(SupportedApp.WHATSAPP, title, messages)
    }

    /**
     * Finds message_text nodes. When the row holding one also holds a `status`
     * (delivery ticks) node, the message is from ME; otherwise position decides.
     */
    private fun collectById(node: ScreenNode, out: MutableList<ReaderRules.Candidate>) {
        val texts = node.children.filter { it.visible && it.shortId == Config.WHATSAPP_MESSAGE_TEXT_ID }
        if (texts.isNotEmpty()) {
            val hasTicks = node.children.any { it.shortId == Config.WHATSAPP_STATUS_ID }
            for (t in texts) {
                val text = t.text ?: continue
                out += ReaderRules.Candidate(text, t.bounds, sender = if (hasTicks) Sender.ME else null)
            }
        }
        for (c in node.children) collectById(c, out)
    }
}
