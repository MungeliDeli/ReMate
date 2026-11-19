package com.replymate.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.replymate.Config
import com.replymate.ai.Mood
import com.replymate.ai.ReplyLength
import com.replymate.ai.ReplyOptions
import com.replymate.ai.Vibe
import com.replymate.overlay.BubblePosition
import com.replymate.overlay.Side
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists settings in app-private Preferences DataStore (spec section 9).
 * Excluded from backups via allowBackup=false + data_extraction_rules.
 * Personas and contact profiles are stored as JSON strings.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL_ID = stringPreferencesKey("model_id")
        val SHARE_FIRST_NAME = booleanPreferencesKey("share_first_name")
        val CONTEXT_SCREENS = intPreferencesKey("context_screens")
        val PERSONAS = stringPreferencesKey("personas_json")
        val DEFAULT_PERSONA = stringPreferencesKey("default_persona_id")
        val CONTACTS = stringPreferencesKey("contacts_json")
        val OPT_MOODS = stringPreferencesKey("reply_moods")
        val OPT_LENGTH = stringPreferencesKey("reply_length")
        val OPT_VIBE = stringPreferencesKey("reply_vibe")
        val BUBBLE_SIDE = stringPreferencesKey("bubble_side")
        val BUBBLE_Y = floatPreferencesKey("bubble_y_fraction")

        // Single-persona fields from v1.0/1.1, read once to migrate.
        val LEGACY_ABOUT_ME = stringPreferencesKey("about_me")
        val LEGACY_WRITING_STYLE = stringPreferencesKey("writing_style")
        val LEGACY_EXAMPLES = stringPreferencesKey("examples")
        val LEGACY_AVOID = stringPreferencesKey("avoid")
        val LEGACY_REPLY_LANGUAGE = stringPreferencesKey("reply_language")
    }

    private val data: Flow<Preferences> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val settings: Flow<AppSettings> = data.map { p ->
        val personas = PersonaStore.decode(p[Keys.PERSONAS], legacy = legacyPersona(p))
        AppSettings(
            displayName = p[Keys.DISPLAY_NAME].orEmpty(),
            apiKey = p[Keys.API_KEY].orEmpty(),
            modelId = p[Keys.MODEL_ID] ?: Config.DEFAULT_MODEL_ID,
            shareFirstName = p[Keys.SHARE_FIRST_NAME] ?: false,
            contextScreens = (p[Keys.CONTEXT_SCREENS] ?: Config.DEFAULT_CONTEXT_SCREENS)
                .coerceIn(1, Config.MAX_CONTEXT_SCREENS),
            personas = personas,
            defaultPersonaId = p[Keys.DEFAULT_PERSONA]?.takeIf { id -> personas.any { it.id == id } }
                ?: personas.first().id,
        )
    }

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.DISPLAY_NAME] = s.displayName
            p[Keys.API_KEY] = s.apiKey.trim()
            p[Keys.MODEL_ID] = s.modelId.trim()
            p[Keys.SHARE_FIRST_NAME] = s.shareFirstName
            p[Keys.CONTEXT_SCREENS] = s.contextScreens.coerceIn(1, Config.MAX_CONTEXT_SCREENS)
            p[Keys.PERSONAS] = PersonaStore.encode(s.personas)
            p[Keys.DEFAULT_PERSONA] = s.defaultPersonaId
            removeLegacy(p)
        }
    }

    private fun legacyPersona(p: Preferences) = Persona(
        id = DEFAULT_PERSONA_ID,
        label = "My persona",
        aboutMe = p[Keys.LEGACY_ABOUT_ME].orEmpty(),
        writingStyle = p[Keys.LEGACY_WRITING_STYLE].orEmpty(),
        examples = p[Keys.LEGACY_EXAMPLES].orEmpty(),
        avoid = p[Keys.LEGACY_AVOID].orEmpty(),
        replyLanguage = p[Keys.LEGACY_REPLY_LANGUAGE]
            ?.let { n -> ReplyLanguage.entries.firstOrNull { it.name == n } } ?: ReplyLanguage.MATCH_CHAT,
    )

    private fun removeLegacy(p: MutablePreferences) {
        p.remove(Keys.LEGACY_ABOUT_ME)
        p.remove(Keys.LEGACY_WRITING_STYLE)
        p.remove(Keys.LEGACY_EXAMPLES)
        p.remove(Keys.LEGACY_AVOID)
        p.remove(Keys.LEGACY_REPLY_LANGUAGE)
    }

    // --- Contact profiles (People tab / panel "Remember for") ---

    /** Keyed by [contactKey]. */
    val contacts: Flow<Map<String, ContactProfile>> = data.map { p -> PersonaStore.decodeContacts(p[Keys.CONTACTS]) }

    suspend fun saveContact(profile: ContactProfile) {
        context.dataStore.edit { p ->
            val map = PersonaStore.decodeContacts(p[Keys.CONTACTS]).toMutableMap()
            map[profile.key] = profile
            p[Keys.CONTACTS] = PersonaStore.encodeContacts(map.values)
        }
    }

    /** Saves [profile], removing [oldKey] first if the name changed. */
    suspend fun replaceContact(oldKey: String?, profile: ContactProfile) {
        context.dataStore.edit { p ->
            val map = PersonaStore.decodeContacts(p[Keys.CONTACTS]).toMutableMap()
            if (oldKey != null) map.remove(oldKey)
            map[profile.key] = profile
            p[Keys.CONTACTS] = PersonaStore.encodeContacts(map.values)
        }
    }

    suspend fun deleteContact(key: String) {
        context.dataStore.edit { p ->
            val map = PersonaStore.decodeContacts(p[Keys.CONTACTS]).toMutableMap()
            map.remove(key)
            p[Keys.CONTACTS] = PersonaStore.encodeContacts(map.values)
        }
    }

    // --- Last style picked in the panel; pre-selected next time. Not chat content. ---

    val replyOptions: Flow<ReplyOptions> = data.map { p ->
        ReplyOptions(
            moods = p[Keys.OPT_MOODS].orEmpty().split(',')
                .mapNotNull { n -> Mood.entries.firstOrNull { it.name == n } }
                .take(ReplyOptions.MAX_MOODS),
            length = p[Keys.OPT_LENGTH]?.let { n -> ReplyLength.entries.firstOrNull { it.name == n } },
            vibe = p[Keys.OPT_VIBE]?.let { n -> Vibe.entries.firstOrNull { it.name == n } },
        )
    }

    suspend fun saveReplyOptions(o: ReplyOptions) {
        context.dataStore.edit { p ->
            p[Keys.OPT_MOODS] = o.moods.joinToString(",") { it.name }
            if (o.length != null) p[Keys.OPT_LENGTH] = o.length.name else p.remove(Keys.OPT_LENGTH)
            if (o.vibe != null) p[Keys.OPT_VIBE] = o.vibe.name else p.remove(Keys.OPT_VIBE)
        }
    }

    // --- Bubble position: kept apart so saving the persona never moves the bubble. ---

    val bubblePosition: Flow<BubblePosition> = data.map { p ->
        BubblePosition(
            side = if (p[Keys.BUBBLE_SIDE] == Side.LEFT.name) Side.LEFT else Side.RIGHT,
            yFraction = p[Keys.BUBBLE_Y] ?: Config.BUBBLE_DEFAULT_Y_FRACTION,
        )
    }

    suspend fun saveBubblePosition(pos: BubblePosition) {
        context.dataStore.edit { p ->
            p[Keys.BUBBLE_SIDE] = pos.side.name
            p[Keys.BUBBLE_Y] = pos.yFraction
        }
    }
}

/** JSON (de)serialisation for personas and contacts; pure so it can be unit-tested. */
object PersonaStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val personaList = ListSerializer(Persona.serializer())
    private val contactList = ListSerializer(ContactProfile.serializer())

    /** Never empty: falls back to [legacy] (the pre-1.2 single persona). */
    fun decode(raw: String?, legacy: Persona): List<Persona> {
        val list = raw?.let { runCatching { json.decodeFromString(personaList, it) }.getOrNull() }.orEmpty()
        return list.ifEmpty { listOf(legacy) }
    }

    fun encode(personas: List<Persona>): String = json.encodeToString(personaList, personas)

    fun decodeContacts(raw: String?): Map<String, ContactProfile> =
        raw?.let { runCatching { json.decodeFromString(contactList, it) }.getOrNull() }
            .orEmpty()
            .associateBy { it.key }

    fun encodeContacts(contacts: Collection<ContactProfile>): String =
        json.encodeToString(contactList, contacts.sortedBy { it.key })
}
