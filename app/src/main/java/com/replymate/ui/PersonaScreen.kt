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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.replymate.data.Persona
import com.replymate.data.newPersonaId
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.saveable.rememberSaveable
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
        suggester = suggester,
    )
}

/** Replaces the persona with the same id. */
private fun AppSettings.withPersona(p: Persona) = copy(personas = personas.map { if (it.id == p.id) p else it })

@Composable
private fun PersonaForm(
    s: AppSettings,
    isSaved: Boolean,
    onChange: (AppSettings) -> Unit,
    suggester: ReplySuggester,
) {
    // Which persona the fields below edit (the default one at first).
    var editingId by rememberSaveable { mutableStateOf(s.defaultPersonaId) }
    val p = s.personas.firstOrNull { it.id == editingId } ?: s.defaultPersona
    fun onPersona(updated: Persona) = onChange(s.withPersona(updated))
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
            "Tell ReplyMate how you write. Keep several personas and pick one per chat. The example messages matter most.",
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

        PersonaSwitcher(
            s = s,
            editing = p,
            onEdit = { editingId = it },
            onChange = onChange,
        )

        OutlinedTextField(
            value = p.label,
            onValueChange = { onPersona(p.copy(label = it.take(30))) },
            label = { Text("Persona name") },
            placeholder = { Text("e.g. Calm Closer") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = p.aboutMe,
            onValueChange = { onPersona(p.copy(aboutMe = it.take(Config.ABOUT_ME_MAX_CHARS))) },
            label = { Text("About me") },
            placeholder = { Text("Age range, vibe, interests…") },
            supportingText = { Text("${p.aboutMe.length} / ${Config.ABOUT_ME_MAX_CHARS}") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = p.writingStyle,
            onValueChange = { onPersona(p.copy(writingStyle = it)) },
            label = { Text("Writing style") },
            placeholder = { Text("e.g. short messages, lowercase, light slang, occasional emoji, mix English with Bemba/Nyanja") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        val lines = countExampleLines(p.examples)
        val tooMany = lines > Config.MAX_EXAMPLE_LINES
        OutlinedTextField(
            value = p.examples,
            onValueChange = { onPersona(p.copy(examples = it)) },
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
            value = p.avoid,
            onValueChange = { onPersona(p.copy(avoid = it)) },
            label = { Text("Things to avoid") },
            placeholder = { Text("e.g. no formal greetings, never use the word dear") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        ReplyLanguagePicker(selected = p.replyLanguage, onSelect = { onPersona(p.copy(replyLanguage = it)) })

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("For all personas", style = MaterialTheme.typography.titleMedium)

        ContextScreensPicker(selected = s.contextScreens, onSelect = { onChange(s.copy(contextScreens = it)) })

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
        TestSection(s, p, suggester)
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
private fun TestSection(s: AppSettings, persona: Persona, suggester: ReplySuggester) {
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
    Text(
        "Uses the persona you're editing: ${persona.label}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (persona.exampleList.isEmpty()) {
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
                state = when (val r = suggester.suggest(s, SampleChat.conversation, label = "Persona test", persona = persona)) {
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

/**
 * Pick which persona to edit, add / duplicate / delete personas and choose
 * the default (used unless a person or the panel picks another).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaSwitcher(
    s: AppSettings,
    editing: Persona,
    onEdit: (String) -> Unit,
    onChange: (AppSettings) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isDefault = editing.id == s.defaultPersonaId
    fun label(p: Persona) = p.label.ifBlank { "Untitled" } + if (p.id == s.defaultPersonaId) "  (default)" else ""

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Personas (${s.personas.size})", style = MaterialTheme.typography.titleSmall)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = label(editing),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Editing") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    s.personas.forEach { p ->
                        DropdownMenuItem(text = { Text(label(p)) }, onClick = { onEdit(p.id); expanded = false })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    val p = Persona(id = newPersonaId(), label = "New persona")
                    onChange(s.copy(personas = s.personas + p))
                    onEdit(p.id)
                }) { Text("New") }
                TextButton(onClick = {
                    val p = editing.copy(id = newPersonaId(), label = (editing.label + " copy").take(30))
                    onChange(s.copy(personas = s.personas + p))
                    onEdit(p.id)
                }) { Text("Duplicate") }
                TextButton(enabled = !isDefault, onClick = { onChange(s.copy(defaultPersonaId = editing.id)) }) {
                    Text(if (isDefault) "Default ✓" else "Make default")
                }
                TextButton(enabled = s.personas.size > 1, onClick = { confirmDelete = true }) { Text("Delete") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${editing.label}\"?") },
            text = { Text("People set to this persona will use your default persona instead.") },
            confirmButton = {
                TextButton(onClick = {
                    val rest = s.personas.filter { it.id != editing.id }
                    val newDefault = if (isDefault) rest.first().id else s.defaultPersonaId
                    onChange(s.copy(personas = rest, defaultPersonaId = newDefault))
                    onEdit(newDefault)
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** How much of the chat to read: ReplyMate scrolls up and back down for 2-3 screens. */
@Composable
private fun ContextScreensPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Chat context", style = MaterialTheme.typography.bodyLarge)
        Text(
            "How much of the chat to read. For 2-3 screens ReplyMate scrolls the chat up and back down (a few seconds).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf("This screen", "2 screens", "3 screens")
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = selected == i + 1,
                    onClick = { onSelect(i + 1) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                ) { Text(label, maxLines = 1) }
            }
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
