package com.replymate.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replymate.BuildConfig
import com.replymate.debug.DebugEntry
import com.replymate.debug.DebugLog
import com.replymate.debug.DebugType
import com.replymate.reader.ChatMessage
import com.replymate.reader.Conversation
import com.replymate.reader.Sender
import com.replymate.service.ServiceState
import com.replymate.service.ServiceStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CAPTURE_DELAY_MS = 5_000L
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

/** Debug tab (spec section 10): lets the app explain itself without USB debugging. */
@Composable
fun DebugScreen() {
    val context = LocalContext.current
    val snapshot by ServiceState.snapshot.collectAsStateWithLifecycle()
    val entries by DebugLog.entries.collectAsStateWithLifecycle()

    var enabledInSettings by remember { mutableStateOf(ServiceStatus.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabledInSettings = ServiceStatus.isEnabled(context)
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Debug", style = MaterialTheme.typography.headlineSmall)
            Text(
                "ReplyMate ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}). " +
                    "Kept in memory only and cleared when the service stops.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Service status", style = MaterialTheme.typography.titleMedium)
                    StatusRow("Enabled in settings", if (enabledInSettings) "Yes" else "No")
                    StatusRow("Running", if (snapshot.running) "Yes" else "No")
                    StatusRow("Last app seen", snapshot.lastPackage ?: "–")
                    StatusRow(
                        "Chat detected",
                        snapshot.chatApp?.let { "Yes (${it.name.lowercase().replaceFirstChar(Char::uppercase)})" } ?: "No",
                    )
                }
            }
        }

        item {
            LastConversationCard(entries.firstOrNull { it.conversation != null }?.conversation)
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Capture node tree", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Records what ReplyMate can see on a screen, so the chat readers can be tuned.\n" +
                            "• In a chat: long-press the bubble.\n" +
                            "• Anywhere else: tap below, then switch to the screen within 5 seconds.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        enabled = snapshot.running,
                        onClick = {
                            if (ServiceState.requestCapture(CAPTURE_DELAY_MS)) {
                                Toast.makeText(context, "Switch to the screen now…", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) { Text("Capture in 5 seconds") }
                    if (!snapshot.running) {
                        Text(
                            "Turn on the accessibility service (Setup tab) to capture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Log (${entries.size}/${DebugLog.CAPACITY})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { DebugLog.clear() }, enabled = entries.isNotEmpty()) { Text("Clear") }
            }
            if (entries.isEmpty()) {
                Text(
                    "Nothing yet. Tap the bubble in a chat to read it, or long-press it to capture a node dump.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(entries, key = { it.id }) { entry -> EntryCard(entry, context) }
    }
}

/** Spec F10 "Last parsed conversation": drawn like a chat so ME/THEM mistakes stand out. */
@Composable
private fun LastConversationCard(conversation: Conversation?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Last parsed conversation", style = MaterialTheme.typography.titleMedium)
            if (conversation == null) {
                Text(
                    "Tap the bubble in a chat to read it. The result shows here: your messages on " +
                        "the right, theirs on the left.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Text(
                "Chat with ${conversation.contactName ?: "(name not found)"} · ${conversation.messages.size} messages, oldest first",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            conversation.messages.forEach { m -> MessageBubble(m) }
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMessage) {
    val isMe = m.sender == Sender.ME
    val label = when (m.sender) {
        Sender.ME -> "ME"
        Sender.THEM -> "THEM"
        Sender.UNKNOWN -> "?"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when (m.sender) {
            Sender.ME -> Arrangement.End
            Sender.THEM -> Arrangement.Start
            Sender.UNKNOWN -> Arrangement.Center
        },
    ) {
        Surface(
            color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(m.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EntryCard(entry: DebugEntry, context: Context) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val isError = entry.type == DebugType.ERROR
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.clickable { expanded = !expanded }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        entry.type.label.uppercase(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(timeFormat.format(Instant.ofEpochMilli(entry.timeMillis)), style = MaterialTheme.typography.labelMedium)
            }
            Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (entry.body.isNotEmpty()) {
                if (expanded) {
                    Text(
                        entry.body,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                } else {
                    Text(
                        entry.body,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { share(context, entry) }) { Text("Share") }
                    OutlinedButton(onClick = { copy(context, entry) }) { Text("Copy") }
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "More") }
                }
            }
        }
    }
}

private fun exportText(e: DebugEntry): String =
    "[${e.type.label}] ${timeFormat.format(Instant.ofEpochMilli(e.timeMillis))} ${e.title}\n\n${e.body}"

private fun share(context: Context, e: DebugEntry) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "ReplyMate ${e.type.label}: ${e.title}")
        putExtra(Intent.EXTRA_TEXT, exportText(e))
    }
    context.startActivity(Intent.createChooser(send, "Share debug entry"))
}

private fun copy(context: Context, e: DebugEntry) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ReplyMate debug", exportText(e)))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
