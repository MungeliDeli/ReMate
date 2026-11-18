package com.replymate.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.replymate.Config
import com.replymate.ReplyMateApplication
import com.replymate.BuildConfig
import com.replymate.debug.DebugLog
import com.replymate.debug.DebugType
import com.replymate.debug.NodeDump
import android.os.Build
import com.replymate.overlay.OverlayController
import com.replymate.reader.NodeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The heart of ReplyMate (spec section 3). In M3 it only decides when to show
 * the bubble. Reading chats is added in M5, and only ever on a bubble tap.
 *
 * onAccessibilityEvent does no real work: it just (re)schedules a debounced
 * check on the main thread.
 */
class ReplyMateAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var overlay: OverlayController? = null
    private var currentApp: SupportedApp? = null
    /** Consecutive foreground checks that got no window at all (transitions). */
    private var emptyChecks = 0

    private val app: ReplyMateApplication get() = application as ReplyMateApplication

    /** Full check: are we on a chat screen (supported app + composer)? */
    private val detectRunnable = Runnable { detectChatScreen() }

    /** Cheap check while the bubble shows: is a chat app still in front? */
    private val foregroundRunnable = Runnable { recheckForeground() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        val controller = OverlayController(
            context = this,
            onBubbleTap = ::onBubbleTapped,
            onBubbleLongPress = { captureNodeTree() },
            onPositionChanged = { pos -> app.appScope.launch { app.settings.saveBubblePosition(pos) } },
        )
        overlay = controller
        serviceScope.launch {
            app.settings.bubblePosition.collect { controller.position = it }
        }
        ServiceState.update { it.copy(running = true) }
        ServiceState.captureHandler = { delayMs ->
            handler.postDelayed({ captureNodeTree() }, delayMs)
        }
        handler.post(detectRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName == null) return
        handler.removeCallbacks(detectRunnable)
        handler.postDelayed(detectRunnable, Config.DETECT_DEBOUNCE_MS)
    }

    private fun detectChatScreen() {
        val controller = overlay ?: return
        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        val pkg = root?.packageName?.toString()
        val detected = try {
            AppDetector.detect(root, controller.screenHeight())
        } catch (e: Exception) {
            DebugLog.error("Chat detection", e)
            null
        } finally {
            NodeUtils.recycle(root)
        }
        if (pkg != null) ServiceState.update { it.copy(lastPackage = pkg) }
        setChatApp(detected)
    }

    private fun recheckForeground() {
        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        val pkg = root?.packageName
        NodeUtils.recycle(root)
        if (pkg != null) ServiceState.update { it.copy(lastPackage = pkg.toString()) }
        emptyChecks = if (pkg == null) emptyChecks + 1 else 0
        val stillInChatApp = AppDetector.appFor(pkg) != null
        // A missing window is often a brief transition; only give up after two in a row.
        if (stillInChatApp || (pkg == null && emptyChecks < 2)) scheduleForegroundCheck() else setChatApp(null)
    }

    private fun setChatApp(detected: SupportedApp?) {
        val controller = overlay ?: return
        currentApp = detected
        ServiceState.update { it.copy(chatApp = detected) }
        emptyChecks = 0
        handler.removeCallbacks(foregroundRunnable)
        if (detected != null) {
            controller.showBubble()
            scheduleForegroundCheck()
        } else {
            controller.hideBubble()
        }
    }

    private fun scheduleForegroundCheck() {
        handler.removeCallbacks(foregroundRunnable)
        handler.postDelayed(foregroundRunnable, Config.FOREGROUND_RECHECK_MS)
    }

    /**
     * Dumps the active window's node tree into the Debug log (spec F10).
     * Triggered by long-pressing the bubble or by "Capture in 5 s" on the Debug tab.
     */
    private fun captureNodeTree() {
        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        if (root == null) {
            DebugLog.add(DebugType.ERROR, "Capture failed", "No active window to read.")
            Toast.makeText(this, "Couldn't capture: no window", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val pkg = root.packageName?.toString() ?: "?"
            val lines = NodeDump.capture(root)
            val screen = overlay?.screenSize()
            val header = buildString {
                append("ReplyMate ${BuildConfig.VERSION_NAME} node dump\n")
                append("package=$pkg  screen=${screen?.x}x${screen?.y}  ")
                append("android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})  ")
                append("device=${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("chatDetected=${currentApp ?: "no"}  nodes=${lines.size}\n\n")
            }
            DebugLog.add(DebugType.NODE_DUMP, "$pkg (${lines.size} nodes)", header + NodeDump.format(lines))
            Toast.makeText(this, "Captured ${lines.size} nodes. Open ReplyMate → Debug to share.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            DebugLog.error("Capture", e)
        } finally {
            NodeUtils.recycle(root)
        }
    }

    private fun onBubbleTapped() {
        // M7 replaces this with the suggestion panel.
        val name = when (currentApp) {
            SupportedApp.WHATSAPP -> "WhatsApp"
            SupportedApp.MESSENGER -> "Messenger"
            null -> "a"
        }
        Toast.makeText(this, "ReplyMate sees $name chat. Long-press to capture it for Debug.", Toast.LENGTH_SHORT).show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onScreenChanged()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ServiceState.reset()
        DebugLog.clear() // spec section 9: debug entries are cleared on service stop
        overlay?.destroy()
        overlay = null
        scope?.cancel()
        scope = null
        super.onDestroy()
    }
}
