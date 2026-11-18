package com.replymate.service

import android.view.accessibility.AccessibilityNodeInfo
import com.replymate.reader.NodeUtils

enum class SupportedApp { WHATSAPP, MESSENGER }

/** Decides whether the current window is a supported chat screen (spec F2). */
object AppDetector {

    fun appFor(packageName: CharSequence?): SupportedApp? = when (packageName?.toString()) {
        "com.whatsapp", "com.whatsapp.w4b" -> SupportedApp.WHATSAPP
        "com.facebook.orca" -> SupportedApp.MESSENGER
        else -> null
    }

    /**
     * Supported app AND a chat composer is present. The chat list, Status/Stories
     * and settings screens have no composer, so they return null.
     */
    fun detect(root: AccessibilityNodeInfo?, screenHeight: Int): SupportedApp? {
        root ?: return null
        val app = appFor(root.packageName) ?: return null
        val composer = NodeUtils.findComposer(root, screenHeight) ?: return null
        NodeUtils.recycle(composer)
        return app
    }
}
