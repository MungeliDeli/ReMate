package com.replymate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replymate.ai.Mood
import com.replymate.ai.ReplyLength
import com.replymate.ai.ReplyOptions
import com.replymate.ai.Vibe
import com.replymate.data.AppSettings
import com.replymate.data.ContactProfile
import com.replymate.data.SettingsRepository
import com.replymate.data.contactKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val NOTE_MAX_CHARS = 300

/**
 * People tab: a note, persona and default style per chat partner. Matched by
 * the chat title, so the name must be written exactly as the chat shows it.
 * The panel's "Add a note" / "Edit note" opens this tab with the name filled in.
 */
@Composable
fun PeopleScreen(
    repo: SettingsRepository,
    appScope: CoroutineScope,
    requestedContact: String?,
    onRequestHandled: () -> Unit,
) {
    val contacts by repo.contacts.collectAsStateWithLifecycle(initialValue = emptyMap())
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    var editing by remember { mutableStateOf<ContactProfile?>(null) }
    var editingOldKey by remember { mutableStateOf<String?>(null) }

    // Opened from the panel: edit that person (new entry if they have none yet).
    LaunchedEffect(requestedContact) {
        val name = requestedContact ?: return@LaunchedEffect
        val existing = repo.contacts.first()[contactKey(name)]
        editing = existing ?: ContactProfile(name)
        editingOldKey = existing?.key
        onRequestHandled()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("People", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Notes, persona and style for specific chats. Easiest way to add someone: in their chat, " +
                    "open the ReplyMate panel and tap \"Add a note\". Names must match the chat title exactly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Button(onClick = { editing = ContactProfile(""); editingOldKey = null }) { Text("Add person") }
        }
        if (contacts.isEmpty()) {
            item {
                Text(
                    "No one yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(contacts.values.sortedBy { it.key }, key = { it.key }) { c ->
            Card(Modifier.fillMaxWidth().clickable { editing = c; editingOldKey = c.key }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(
                            "Persona: " + (c.personaId?.let { id -> settings.personas.firstOrNull { it.id == id }?.label } ?: "default"),
                            c.options?.let { "Style: ${it.summary()}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (c.note.isNotBlank()) {
                        Text(c.note, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    editing?.let { profile ->
        ContactEditor(
            initial = profile,
            settings = settings,
            isNew = editingOldKey == null,
            onDismiss = { editing = null },
            onSave = { updated ->
                val oldKey = editingOldKey
                appScope.launch { repo.replaceContact(oldKey, updated) }
                editing = null
            },
            onDelete = {
                val oldKey = editingOldKey
                if (oldKey != null) appScope.launch { repo.deleteContact(oldKey) }
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactEditor(
    initial: ContactProfile,
    settings: AppSettings,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ContactProfile) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var note by remember(initial) { mutableStateOf(initial.note) }
    var personaId by remember(initial) { mutableStateOf(initial.personaId) }
    var options by remember(initial) { mutableStateOf(initial.options ?: ReplyOptions()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add person" else "Edit ${initial.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name (as the chat title shows it)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(NOTE_MAX_CHARS) },
                    label = { Text("Note for ReplyMate") },
                    placeholder = { Text("e.g. my crush, we met at uni, keep it flirty but respectful") },
                    supportingText = { Text("${note.length} / $NOTE_MAX_CHARS · sent to Gemini (redacted)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                val personaLabel = personaId?.let { id -> settings.personas.firstOrNull { it.id == id }?.label } ?: "Default persona"
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = personaLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Persona for them") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Default persona") }, onClick = { personaId = null; expanded = false })
                        settings.personas.forEach { p ->
                            DropdownMenuItem(text = { Text(p.label) }, onClick = { personaId = p.id; expanded = false })
                        }
                    }
                }

                Text("Default style for them (optional)", style = MaterialTheme.typography.labelLarge)
                ChipGroup("Mood (up to ${ReplyOptions.MAX_MOODS})", Mood.entries.map { it.label to (it in options.moods) }) { i ->
                    options = options.toggle(Mood.entries[i])
                }
                ChipGroup("Length", ReplyLength.entries.map { it.label to (options.length == it) }) { i ->
                    options = options.toggle(ReplyLength.entries[i])
                }
                ChipGroup("Vibe", Vibe.entries.map { it.label to (options.vibe == it) }) { i ->
                    options = options.toggle(Vibe.entries[i])
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        ContactProfile(
                            name = name.trim(),
                            note = note.trim(),
                            personaId = personaId,
                            options = options.takeUnless { it.isAuto },
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ChipGroup(title: String, chips: List<Pair<String, Boolean>>, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEachIndexed { i, (label, selected) ->
                FilterChip(selected = selected, onClick = { onToggle(i) }, label = { Text(label) })
            }
        }
    }
}
