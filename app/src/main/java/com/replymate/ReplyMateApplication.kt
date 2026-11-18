package com.replymate

import android.app.Application
import com.replymate.ai.GeminiClient
import com.replymate.ai.ReplySuggester
import com.replymate.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Holds the few app-wide singletons. No DI framework: this app is small. */
class ReplyMateApplication : Application() {
    /** Lives as long as the process; used for writes that must outlive a screen. */
    val appScope = CoroutineScope(SupervisorJob())

    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    /** One HTTP client for the whole app. */
    val suggester: ReplySuggester by lazy { ReplySuggester(GeminiClient()) }
}
