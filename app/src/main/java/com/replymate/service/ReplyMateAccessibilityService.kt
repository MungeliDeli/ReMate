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
import com.replymate.reader.MessengerReader
import com.replymate.reader.NodeUtils
import com.replymate.reader.ScreenNode
import com.replymate.reader.ScreenSnapshot
import com.replymate.reader.Sender
import com.replymate.reader.WhatsAppReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The heart of ReplyMate (spec section 3). Decides when to show the bubble and,
 * only when the bubble is tapped, reads the open chat.
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

    /** Live root of the active window, or null. Caller recycles it. */
    private fun activeRoot() = try { rootInActiveWindow } catch (_: Exception) { null }

    /** Freezes the active window into a [ScreenNode] tree, or null. */
    private fun snapshotActiveWindow(): ScreenNode? {
        val root = activeRoot() ?: return null
        return try {
            ScreenSnapshot.from(root)
        } catch (e: Exception) {
            DebugLog.error("Snapshot", e)
            null
        } finally {
            NodeUtils.recycle(root)
        }
    }

    /**
     * Dumps the active window's node tree into the Debug log (spec F10).
     * Triggered by long-pressing the bubble, "Capture in 5 s" on the Debug tab,
     * or automatically when a chat can't be read (spec section 12).
     */
    private fun captureNodeTree(existing: ScreenNode? = null, reason: String? = null, showToast: Boolean = true) {
        val snap = existing ?: snapshotActiveWindow()
        if (snap == null) {
            DebugLog.add(DebugType.ERROR, "Capture failed", "No active window to read.")
            if (showToast) toast("Couldn't capture: no window")
            return
        }
        val pkg = snap.packageName ?: "?"
        val nodes = snap.count()
        val screen = overlay?.screenSize()
        val header = buildString {
            append("ReplyMate ${BuildConfig.VERSION_NAME} node dump\n")
            reason?.let { append("reason=$it\n") }
            append("package=$pkg  screen=${screen?.x}x${screen?.y}  ")
            append("android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})  ")
            append("device=${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("chatDetected=${currentApp ?: "no"}  nodes=$nodes\n\n")
        }
        DebugLog.add(DebugType.NODE_DUMP, "$pkg ($nodes nodes)", header + NodeDump.format(snap))
        if (showToast) toast("Captured $nodes nodes. Open ReplyMate → Debug to share.", long = true)
    }

    /**
     * Bubble tap (spec 2.2). In M5 this reads the open chat and shows the result
     * in the Debug tab; M7 replaces the toast with the suggestion panel.
     */
    private fun onBubbleTapped() {
        val app = currentApp ?: return
        val snap = snapshotActiveWindow()
        if (snap == null) {
            DebugLog.add(DebugType.ERROR, "Couldn't read this chat", "No active window.")
            toast("Couldn't read this chat")
            return
        }
        val screen = overlay?.screenSize() ?: return
        val reader = when (app) {
            SupportedApp.WHATSAPP -> WhatsAppReader
            SupportedApp.MESSENGER -> MessengerReader
        }
        val conversation = try {
            reader.readConversation(snap, screen.x, screen.y)
        } catch (e: Exception) {
            DebugLog.error("Read chat", e)
            null
        }
        if (conversation == null) {
            DebugLog.add(DebugType.ERROR, "Couldn't read this chat", "No messages found. A node dump was captured automatically.")
            captureNodeTree(existing = snap, reason = "no messages read", showToast = false)
            toast("Couldn't read this chat. A node dump was saved in Debug.", long = true)
            return
        }
        val me = conversation.messages.count { it.sender == Sender.ME }
        val them = conversation.messages.count { it.sender == Sender.THEM }
        DebugLog.add(
            DebugType.CONVERSATION,
            "${app.name.lowercase().replaceFirstChar(Char::uppercase)} · ${conversation.messages.size} messages",
            conversation.describe(),
            conversation = conversation,
        )
        toast("Read ${conversation.messages.size} messages ($me from you, $them from them). See ReplyMate → Debug.", long = true)
    }

    private fun toast(text: String, long: Boolean = false) {
        Toast.makeText(this, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
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
