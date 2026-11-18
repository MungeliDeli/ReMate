package com.replymate.reader

import com.replymate.service.SupportedApp

/**
 * Messenger (spec F3). Messenger exposes few stable view IDs, so this walks the
 * biggest scrollable list and collects text (or descriptions).
 *
 * Tuned on a real dump (Messenger, Pixel 6, Nov 2026): each real message reads
 * "<Sender first name>, <message>, double tap to see sent/receive date and
 * time, double tap and hold to react on message". Quoted messages, "You
 * replied to …" headers, time chips and "Delivered …" labels lack that ending.
 * So when that format is present, only text in it is kept, the name prefix
 * decides the sender, and both the prefix and the ending are removed.
 * Without it (other versions/languages), position decides as before.
 */
object MessengerReader : ChatReader {

    private val hintSuffix = Regex(
        """,\s*double tap to see sent/receive date and time(,\s*double tap and hold to react on message)?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    /** "Name, message" where Name is 1-3 words. */
    private val namePrefix = Regex("""^([\p{L}\p{M}'.\-]+(?: [\p{L}\p{M}'.\-]+){0,2}), (.+)$""", RegexOption.DOT_MATCHES_ALL)

    override fun readConversation(root: ScreenNode, screenWidth: Int, screenHeight: Int): Conversation? {
        val list = ReaderRules.messageList(root) ?: root
        val raw = ReaderRules.collectTexts(list) { n -> n.simpleClass.contains("Button") }
        val title = ReaderRules.toolbarTitle(root, screenHeight) { n -> n.simpleClass.contains("Button") }

        val tagged = raw.filter { hintSuffix.containsMatchIn(it.text) }
        val candidates = if (tagged.isNotEmpty()) tagged.map { parseTagged(it, title) } else raw

        val messages = ReaderRules.toMessages(candidates, screenWidth)
        if (messages.isEmpty()) return null
        return Conversation(SupportedApp.MESSENGER, title, messages)
    }

    /** Strips the ending and the sender prefix; THEM when the prefix is the contact's name. */
    internal fun parseTagged(c: ReaderRules.Candidate, contactName: String?): ReaderRules.Candidate {
        val body = c.text.replace(hintSuffix, "").trim()
        val m = namePrefix.find(body) ?: return c.copy(text = body)
        val name = m.groupValues[1]
        val text = m.groupValues[2].trim()
        val sender = when {
            contactName.isNullOrBlank() -> null // unknown: let position decide
            isContact(name, contactName) -> Sender.THEM
            else -> Sender.ME
        }
        return c.copy(text = text, sender = sender)
    }

    private fun isContact(name: String, contactName: String): Boolean {
        val n = name.trim().lowercase()
        val full = contactName.trim().lowercase()
        return n == full || n == full.substringBefore(' ')
    }
}
