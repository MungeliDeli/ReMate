package com.replymate.ui

import androidx.compose.runtime.Composable
import com.replymate.BuildConfig

@Composable
fun DebugScreen() {
    Placeholder(
        title = "Debug",
        body = "ReplyMate ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}).\n\n" +
            "Service status, node dumps and request logs arrive in milestone 4.",
    )
}
