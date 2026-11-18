package com.replymate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.replymate.BuildConfig

@Composable
fun DebugScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Debug", style = MaterialTheme.typography.headlineSmall)
        Text("ReplyMate ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
        Text(
            "Service status, node dumps and request logs arrive in milestone 4.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
