package com.replymate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.replymate.Config
import com.replymate.ai.GeminiResult
import com.replymate.ai.ReplySuggester
import com.replymate.ai.SampleChat
import com.replymate.ai.Suggestion
import com.replymate.reader.Sender
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.sp
import com.replymate.data.AppSettings
import com.replymate.data.ReplyLanguage
import com.replymate.data.SettingsRepository
import com.replymate.data.countExampleLines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Persona + Gemini settings (spec F8). Edits live in a local draft and are
 * saved automatically shortly after typing stops, and again when leaving the tab.
 */
@Composable
fun PersonaScreen(repo: SettingsRepository, appScope: CoroutineScope, suggester: ReplySuggester) {
    var draft by remember { mutableStateOf<AppSettings?>(null) }
    var saved by remember { mutableStateOf<AppSettings?>(null) }

    LaunchedEffect(Unit) {
        val stored = repo.settings.first()
        draft = stored
        saved = stored
    }

    // Debounced autosave.
    LaunchedEffect(draft) {
        val d = draft ?: return@LaunchedEffect
        if (d == saved) return@LaunchedEffect
        delay(Config.AUTOSAVE_DEBOUNCE_MS)
        repo.save(d)
        saved = d
    }

    // Save anything still pending when the tab is left (outlives this screen).
    val latestDraft by rememberUpdatedState(draft)
    val latestSaved by rememberUpdatedState(saved)
    DisposableEffect(Unit) {
        onDispose {
            val d = latestDraft
            if (d != null && d != latestSaved) appScope.launch { repo.save(d) }
        }
    }

    val s = draft
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    PersonaForm(
        s = s,
        isSaved = s == saved,
        onChange = { draft = it },
        testSection = { TestSection(s, suggester) },
    )
}

@Composable
private fun PersonaForm(
    s: AppSettings,
    isSaved: Boolean,
    onChange: (AppSettings) -> Unit,
    testSection: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Persona", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text(
                if (isSaved) "Saved" else "Saving…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Tell ReplyMate how you write. The example messages matter most.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = s.displayName,
            onValueChange = { onChange(s.copy(displayName = it)) },
            label = { Text("Your first name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = s.aboutMe,
            onValueChange = { onChange(s.copy(aboutMe = it.take(Config.ABOUT_ME_MAX_CHARS))) },
            label = { Text("About me") },
            placeholder = { Text("Age range, vibe, interests…") },
            supportingText = { Text("${s.aboutMe.length} / ${Config.ABOUT_ME_MAX_CHARS}") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = s.writingStyle,
            onValueChange = { onChange(s.copy(writingStyle = it)) },
            label = { Text("Writing style") },
            placeholder = { Text("e.g. short messages, lowercase, light slang, occasional emoji, mix English with Bemba/Nyanja") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        val lines = countExampleLines(s.examples)
        val tooMany = lines > Config.MAX_EXAMPLE_LINES
        OutlinedTextField(
            value = s.examples,
            onValueChange = { onChange(s.copy(examples = it)) },
            label = { Text("Example messages (one per line)") },
            placeholder = { Text("Paste ${Config.RECOMMENDED_EXAMPLES} messages you've really sent, one per line") },
            supportingText = {
                Text(
                    if (tooMany) "$lines lines. Only the first ${Config.MAX_EXAMPLE_LINES} are used."
                    else "$lines / ${Config.MAX_EXAMPLE_LINES} (${Config.RECOMMENDED_EXAMPLES} recommended)",
                )
            },
            isError = tooMany,
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = s.avoid,
            onValueChange = { onChange(s.copy(avoid = it)) },
            label = { Text("Things to avoid") },
            placeholder = { Text("e.g. no formal greetings, never use the word dear") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        ReplyLanguagePicker(selected = s.replyLanguage, onSelect = { onChange(s.copy(replyLanguage = it)) })

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Gemini", style = MaterialTheme.typography.titleMedium)

        var showKey by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = s.apiKey,
            onValueChange = { onChange(s.copy(apiKey = it.trim())) },
            label = { Text("Gemini API key") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
            },
            supportingText = { Text("Stored only on this phone. Get one at aistudio.google.com/apikey") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = s.modelId,
            onValueChange = { onChange(s.copy(modelId = it.trim())) },
            label = { Text("Model ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            supportingText = { Text("Default: ${Config.DEFAULT_MODEL_ID}") },
            trailingIcon = {
                if (s.modelId != Config.DEFAULT_MODEL_ID) {
                    TextButton(onClick = { onChange(s.copy(modelId = Config.DEFAULT_MODEL_ID)) }) { Text("Reset") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Share first name with AI", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Off: the other person is called \"Them\". On: only their first name is sent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.shareFirstName, onCheckedChange = { onChange(s.copy(shareFirstName = it)) })
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        testSection()
    }
}

private sealed interface TestState {
    data object Idle : TestState
    data object Loading : TestState
    data class Done(val suggestions: List<Suggestion>, val latencyMs: Long) : TestState
    data class Failed(val message: String) : TestState
}

/**
 * Persona "Test" button (spec F8): sends a fixed sample chat with the current
 * (even unsaved) persona and shows the 3 results. Verifies key + persona.
 */
@Composable
private fun TestSection(s: AppSettings, suggester: ReplySuggester) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<TestState>(TestState.Idle) }

    Text("Test", style = MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Sample chat", style = MaterialTheme.typography.labelMedium)
            SampleChat.conversation.messages.forEach { m ->
                Text(
                    "${if (m.sender == Sender.ME) "Me" else "Them"}: ${m.text}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (s.exampleList.isEmpty()) {
        Text(
            "Add example messages for better replies.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(
        enabled = state != TestState.Loading,
        onClick = {
            state = TestState.Loading
            scope.launch {
                state = when (val r = suggester.suggest(s, SampleChat.conversation, label = "Persona test")) {
                    is GeminiResult.Success -> TestState.Done(r.suggestions, r.latencyMs)
                    is GeminiResult.Failure -> TestState.Failed(r.error.userMessage)
                }
            }
        },
    ) { Text(if (state == TestState.Loading) "Thinking…" else "Test with sample chat") }

    when (val st = state) {
        TestState.Idle -> Unit
        TestState.Loading -> CircularProgressIndicator(Modifier.padding(8.dp))
        is TestState.Failed -> Text(st.message, color = MaterialTheme.colorScheme.error)
        is TestState.Done -> {
            Text(
                "${st.suggestions.size} suggestions in ${st.latencyMs} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            st.suggestions.forEach { SuggestionCard(it) }
        }
    }
}

@Composable
private fun SuggestionCard(sug: Suggestion) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp)) {
                Text(
                    sug.tone.uppercase(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(sug.text, fontSize = 15.sp, lineHeight = 20.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplyLanguagePicker(selected: ReplyLanguage, onSelect: (ReplyLanguage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Reply language") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReplyLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.label) },
                    onClick = { onSelect(lang); expanded = false },
                )
            }
        }
    }
}
