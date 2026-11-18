package com.replymate.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class DebugType(val label: String) {
    NODE_DUMP("Node dump"),
    CONVERSATION("Conversation"),
    REQUEST("Request"),
    RESPONSE("Response"),
    ERROR("Error"),
    INFO("Info"),
}

data class DebugEntry(
    val id: Long,
    val timeMillis: Long,
    val type: DebugType,
    val title: String,
    val body: String,
)

/**
 * In-memory ring buffer of the last [CAPACITY] debug entries (spec section 10).
 * Never written to disk or Logcat. Cleared when the service stops or on "Clear".
 */
object DebugLog {
    const val CAPACITY = 50

    private val nextId = AtomicLong(1)
    private val _entries = MutableStateFlow<List<DebugEntry>>(emptyList())

    /** Newest first. */
    val entries: StateFlow<List<DebugEntry>> = _entries.asStateFlow()

    fun add(type: DebugType, title: String, body: String = "", now: Long = System.currentTimeMillis()) {
        val entry = DebugEntry(nextId.getAndIncrement(), now, type, title, body)
        _entries.update { (listOf(entry) + it).take(CAPACITY) }
    }

    fun error(where: String, e: Throwable) {
        add(DebugType.ERROR, "$where: ${e.javaClass.simpleName}", e.message.orEmpty())
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
