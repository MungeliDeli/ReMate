package com.replymate.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replymate.Config
import com.replymate.data.AppSettings
import com.replymate.data.SettingsRepository
import com.replymate.service.ServiceState
import com.replymate.service.ServiceStatus
import android.os.PowerManager

@Composable
fun SetupScreen(repo: SettingsRepository, onOpenPersona: () -> Unit) {
    val context = LocalContext.current
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    val snapshot by ServiceState.snapshot.collectAsStateWithLifecycle()

    // Re-check every time the owner comes back from system settings.
    var enabled by remember { mutableStateOf(ServiceStatus.isEnabled(context)) }
    var batteryUnrestricted by remember { mutableStateOf(isBatteryUnrestricted(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = ServiceStatus.isEnabled(context)
        batteryUnrestricted = isBatteryUnrestricted(context)
        onPauseOrDispose { }
    }
    // Spec section 12 "Service killed by system": switched on in Settings is not
    // enough; it must also be running.
    val serviceOn = enabled && snapshot.running
    val serviceStuck = enabled && !snapshot.running

    val doneCount = listOf(settings.hasApiKey, settings.personaReady, serviceOn).count { it }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Setup", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (doneCount == 3) "All set. Open a WhatsApp or Messenger chat to use ReplyMate."
            else "$doneCount of 3 done",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ChecklistItem(
            done = settings.hasApiKey,
            title = "1. Gemini API key",
            body = if (settings.hasApiKey) "Key saved on this phone."
            else "Get a free key at aistudio.google.com/apikey, then paste it on the Persona tab.",
        ) {
            if (!settings.hasApiKey) {
                Button(onClick = onOpenPersona) { Text("Add API key") }
                OutlinedButton(onClick = { openUrl(context, "https://aistudio.google.com/apikey") }) {
                    Text("Get a key")
                }
            }
        }

        val exampleCount = settings.defaultPersona.exampleList.size
        ChecklistItem(
            done = settings.personaReady,
            title = "2. Your persona",
            body = when {
                settings.displayName.isBlank() -> "Add your name and some messages you've really sent."
                exampleCount < Config.MIN_EXAMPLES_FOR_READY ->
                    "$exampleCount example messages so far. Add at least ${Config.MIN_EXAMPLES_FOR_READY} " +
                        "(${Config.RECOMMENDED_EXAMPLES} is best)."
                else -> "$exampleCount example messages. More examples = replies that sound more like you."
            },
        ) {
            OutlinedButton(onClick = onOpenPersona) {
                Text(if (settings.personaReady) "Edit persona" else "Fill in persona")
            }
        }

        ChecklistItem(
            done = serviceOn,
            title = "3. Accessibility service",
            body = when {
                serviceOn -> "ReplyMate is switched on and running."
                serviceStuck -> "Switched on, but not running. Android probably stopped it to save battery. " +
                    "Turn ReplyMate off and on again in Accessibility, and set its battery use to Unrestricted (below)."
                else -> "Lets ReplyMate show its button in WhatsApp and Messenger and read the open chat " +
                    "when you tap it. Find ReplyMate in the list and switch it on."
            },
        ) {
            if (!serviceOn) {
                Button(onClick = { openAccessibilitySettings(context) }) {
                    Text(if (serviceStuck) "Open Accessibility" else "Enable service")
                }
            }
        }

        if (!enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RestrictedSettingsHelp(onOpenAppInfo = { openAppInfo(context) })
        }

        BatteryTip(unrestricted = batteryUnrestricted, onOpenAppInfo = { openAppInfo(context) })
    }
}

@Composable
private fun ChecklistItem(
    done: Boolean,
    title: String,
    body: String,
    actions: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.Info,
                contentDescription = if (done) "Done" else "Not done",
                tint = if (done) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
    }
}

@Composable
private fun RestrictedSettingsHelp(onOpenAppInfo: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ReplyMate greyed out or blocked?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Android blocks accessibility for apps installed from an APK until you allow it:\n" +
                    "1. Tap \"Open App info\" below.\n" +
                    "2. Tap the ⋮ menu (top right) → \"Allow restricted settings\".\n" +
                    "3. Come back and tap \"Enable service\" again.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onOpenAppInfo) { Text("Open App info") }
        }
    }
}

/**
 * Spec section 12: if Android kills the service, the bubble disappears. Setting
 * battery use to Unrestricted prevents that. Shown as a tip, not a checklist step.
 */
@Composable
private fun BatteryTip(unrestricted: Boolean, onOpenAppInfo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (unrestricted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (unrestricted) Icons.Filled.CheckCircle else Icons.Outlined.Info,
                contentDescription = if (unrestricted) "Done" else "Tip",
                tint = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tip: keep ReplyMate running", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                if (unrestricted) {
                    Text("Battery use is Unrestricted, so Android won't stop ReplyMate.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "If the bubble sometimes stops appearing, Android is stopping ReplyMate to save battery.\n" +
                            "1. Tap \"Open App info\".\n" +
                            "2. Open \"App battery usage\" (or \"Battery\").\n" +
                            "3. Choose \"Unrestricted\" (or turn on \"Allow background usage\").",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onOpenAppInfo) { Text("Open App info") }
                }
            }
        }
    }
}

private fun isBatteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

private fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
