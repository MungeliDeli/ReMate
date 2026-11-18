package com.replymate.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ServiceSnapshot(
    val running: Boolean = false,
    /** Last foreground package the service looked at (it only sees chat apps). */
    val lastPackage: String? = null,
    val chatApp: SupportedApp? = null,
)

/**
 * Live status shared between the service and the Debug screen (same process).
 * Memory only.
 */
object ServiceState {
    private val _snapshot = MutableStateFlow(ServiceSnapshot())
    val snapshot: StateFlow<ServiceSnapshot> = _snapshot.asStateFlow()

    /** Set by the running service; captures the active window after a delay. */
    @Volatile
    internal var captureHandler: ((delayMs: Long) -> Unit)? = null

    fun requestCapture(delayMs: Long): Boolean {
        val h = captureHandler ?: return false
        h(delayMs)
        return true
    }

    internal fun update(block: (ServiceSnapshot) -> ServiceSnapshot) = _snapshot.update(block)

    internal fun reset() {
        captureHandler = null
        _snapshot.value = ServiceSnapshot()
    }
}
