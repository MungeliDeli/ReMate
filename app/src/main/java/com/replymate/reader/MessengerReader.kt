package com.replymate.reader

import com.replymate.service.SupportedApp

/**
 * Messenger (spec F3). Messenger exposes few stable view IDs, so this walks the
 * biggest scrollable list and collects text (or descriptions), then relies on
 * position for the sender. Expect to tune it with Debug node dumps.
 */
object MessengerReader : ChatReader {

    override fun readConversation(root: ScreenNode, screenWidth: Int, screenHeight: Int): Conversation? {
        val list = ReaderRules.messageList(root) ?: root
        val candidates = ReaderRules.collectTexts(list) { n -> n.simpleClass.contains("Button") }
        val messages = ReaderRules.toMessages(candidates, screenWidth)
        if (messages.isEmpty()) return null
        val title = ReaderRules.toolbarTitle(root, screenHeight) { n -> n.simpleClass.contains("Button") }
        return Conversation(SupportedApp.MESSENGER, title, messages)
    }
}
