package com.replymate.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object ServiceStatus {
    /** True when the owner has switched ReplyMate on under Settings > Accessibility. */
    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context, ReplyMateAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
