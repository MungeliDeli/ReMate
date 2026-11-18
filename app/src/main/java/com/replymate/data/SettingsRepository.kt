package com.replymate.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.replymate.Config
import com.replymate.overlay.BubblePosition
import com.replymate.overlay.Side
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists settings in app-private Preferences DataStore (spec section 9).
 * Excluded from backups via allowBackup=false + data_extraction_rules.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ABOUT_ME = stringPreferencesKey("about_me")
        val WRITING_STYLE = stringPreferencesKey("writing_style")
        val EXAMPLES = stringPreferencesKey("examples")
        val AVOID = stringPreferencesKey("avoid")
        val REPLY_LANGUAGE = stringPreferencesKey("reply_language")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL_ID = stringPreferencesKey("model_id")
        val SHARE_FIRST_NAME = booleanPreferencesKey("share_first_name")
        val BUBBLE_SIDE = stringPreferencesKey("bubble_side")
        val BUBBLE_Y = floatPreferencesKey("bubble_y_fraction")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                displayName = p[Keys.DISPLAY_NAME].orEmpty(),
                aboutMe = p[Keys.ABOUT_ME].orEmpty(),
                writingStyle = p[Keys.WRITING_STYLE].orEmpty(),
                examples = p[Keys.EXAMPLES].orEmpty(),
                avoid = p[Keys.AVOID].orEmpty(),
                replyLanguage = p[Keys.REPLY_LANGUAGE]
                    ?.let { name -> ReplyLanguage.entries.firstOrNull { it.name == name } }
                    ?: ReplyLanguage.MATCH_CHAT,
                apiKey = p[Keys.API_KEY].orEmpty(),
                modelId = p[Keys.MODEL_ID] ?: Config.DEFAULT_MODEL_ID,
                shareFirstName = p[Keys.SHARE_FIRST_NAME] ?: false,
            )
        }

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.DISPLAY_NAME] = s.displayName
            p[Keys.ABOUT_ME] = s.aboutMe
            p[Keys.WRITING_STYLE] = s.writingStyle
            p[Keys.EXAMPLES] = s.examples
            p[Keys.AVOID] = s.avoid
            p[Keys.REPLY_LANGUAGE] = s.replyLanguage.name
            p[Keys.API_KEY] = s.apiKey.trim()
            p[Keys.MODEL_ID] = s.modelId.trim()
            p[Keys.SHARE_FIRST_NAME] = s.shareFirstName
        }
    }

    /** Kept apart from [AppSettings] so saving the persona never moves the bubble. */
    val bubblePosition: Flow<BubblePosition> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
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
