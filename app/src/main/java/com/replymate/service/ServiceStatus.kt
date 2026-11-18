package com.replymate.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object ServiceStatus {
    /**
     * Fully-qualified name of the accessibility service. The class itself is
     * added in milestone M3; until then this always reports "off".
     */
    const val SERVICE_CLASS = "com.replymate.service.ReplyMateAccessibilityService"

    /** True when the owner has switched ReplyMate on under Settings > Accessibility. */
    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context.packageName, SERVICE_CLASS)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
