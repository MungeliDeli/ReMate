package com.replymate.insert

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.replymate.reader.NodeUtils

/**
 * Puts a suggestion into the chat's message box (spec F6).
 * It only ever sets text. No code path here (or anywhere) presses Send.
 */
object TextInserter {

    /**
     * Finds the composer in [chatRoot] (WhatsApp by view ID, otherwise the lowest
     * editable box) and replaces its text. Returns false if no box was found or
     * the app refused the action; the caller then falls back to the clipboard.
     */
    fun insert(chatRoot: AccessibilityNodeInfo, text: String, screenHeight: Int): Boolean {
        val composer = NodeUtils.findComposer(chatRoot, screenHeight) ?: return false
        return try {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val ok = composer.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok) {
                // Put the cursor at the end so the owner can keep typing.
                val sel = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
                }
                runCatching { composer.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel) }
            }
            ok
        } catch (_: Exception) {
            false
        } finally {
            NodeUtils.recycle(composer)
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ReplyMate suggestion", text))
    }
}
