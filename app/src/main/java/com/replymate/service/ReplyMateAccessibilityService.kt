package com.replymate.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.replymate.BuildConfig
import com.replymate.Config
import com.replymate.MainActivity
import com.replymate.ReplyMateApplication
import com.replymate.ai.Suggestion
import com.replymate.debug.DebugLog
import com.replymate.debug.DebugType
import com.replymate.debug.NodeDump
import com.replymate.insert.TextInserter
import com.replymate.overlay.OverlayController
import com.replymate.overlay.PanelAction
import com.replymate.overlay.PanelState
import com.replymate.overlay.SuggestionPanelView
import com.replymate.reader.Conversation
import com.replymate.reader.MessengerReader
import com.replymate.reader.NodeUtils
import com.replymate.reader.ScreenNode
import com.replymate.reader.ScreenSnapshot
import com.replymate.reader.WhatsAppReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The heart of ReplyMate (spec section 3): shows the bubble on chat screens and,
 * only when it is tapped, reads the open chat, asks Gemini, shows the panel and
 * inserts the reply the owner picks. It never sends anything.
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

    /** The chat being answered; memory only, dropped when the panel closes (spec section 9). */
    private var panelConversation: Conversation? = null
    private var requestJob: Job? = null
    private var lastOutsideCloseAt = 0L

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

    // ---------------------------------------------------------------- detection

    private fun detectChatScreen() {
        val controller = overlay ?: return
        val root = chatWindowRoot()
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
        if (controller.isPanelOpen) controller.setPanelBottomOffset(panelBottomOffset())
        setChatApp(detected)
    }

    private fun recheckForeground() {
        val root = activeRoot()
        val pkg = root?.packageName?.toString()
        NodeUtils.recycle(root)
        if (pkg != null && pkg != packageName) ServiceState.update { it.copy(lastPackage = pkg) }
        emptyChecks = if (pkg == null) emptyChecks + 1 else 0
        // Our own overlay (bubble/panel) can briefly count as "active" while it is
        // touched; that is still the chat. The ReplyMate app itself is not.
        val stillInChatApp = AppDetector.appFor(pkg) != null || (pkg == packageName && activeWindowIsOverlay())
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
            closePanel() // spec 2.3: leaving the chat hides button and panel
            controller.hideBubble()
        }
    }

    private fun scheduleForegroundCheck() {
        handler.removeCallbacks(foregroundRunnable)
        handler.postDelayed(foregroundRunnable, Config.FOREGROUND_RECHECK_MS)
    }

    // ---------------------------------------------------------------- windows

    /** Live root of the active window, or null. Caller recycles it. */
    private fun activeRoot() = try { rootInActiveWindow } catch (_: Exception) { null }

    /**
     * Root of the chat app's window. Normally the active window, but while our
     * own overlay is touched it may be "active", so fall back to scanning the
     * window list for a supported app. Caller recycles it.
     */
    private fun chatWindowRoot(): AccessibilityNodeInfo? {
        val active = activeRoot()
        if (active != null && active.packageName?.toString() != packageName) return active
        NodeUtils.recycle(active)
        val list = try { windows } catch (_: Exception) { emptyList() }
        var found: AccessibilityNodeInfo? = null
        for (w in list) {
            if (found == null && w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val r = try { w.root } catch (_: Exception) { null }
                if (r != null && AppDetector.appFor(r.packageName) != null) found = r else NodeUtils.recycle(r)
            }
            recycleWindow(w)
        }
        return found
    }

    private fun activeWindowIsOverlay(): Boolean {
        val list = try { windows } catch (_: Exception) { emptyList() }
        var overlayActive = false
        for (w in list) {
            if (w.isActive && w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) overlayActive = true
            recycleWindow(w)
        }
        return overlayActive
    }

    /** Freezes the chat window into a [ScreenNode] tree, or null. */
    private fun snapshotChatWindow(): ScreenNode? {
        val root = chatWindowRoot() ?: return null
        return try {
            ScreenSnapshot.from(root)
        } catch (e: Exception) {
            DebugLog.error("Snapshot", e)
            null
        } finally {
            NodeUtils.recycle(root)
        }
    }

    /** Distance from the screen bottom to keep the panel above the keyboard / nav bar. */
    private fun panelBottomOffset(): Int {
        val controller = overlay ?: return 0
        val screenH = controller.screenSize().y
        var ime = 0
        val list = try { windows } catch (_: Exception) { emptyList() }
        for (w in list) {
            if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                val r = Rect()
                w.getBoundsInScreen(r)
                if (r.height() > 0) ime = (screenH - r.top).coerceAtLeast(0)
            }
            recycleWindow(w)
        }
        return maxOf(ime, navigationBarHeight())
    }

    private fun navigationBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
            return wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
        }
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun recycleWindow(w: AccessibilityWindowInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            runCatching { w.recycle() }
        }
    }

    // ---------------------------------------------------------------- debug capture

    /**
     * Dumps the chat window's node tree into the Debug log (spec F10).
     * Triggered by long-pressing the bubble, the panel's overflow, "Capture in 5 s"
     * on the Debug tab, or automatically when a chat can't be read (section 12).
     */
    private fun captureNodeTree(existing: ScreenNode? = null, reason: String? = null, showToast: Boolean = true) {
        val snap = existing ?: snapshotChatWindow() ?: activeRoot()?.let { r ->
            try { ScreenSnapshot.from(r) } finally { NodeUtils.recycle(r) }
        }
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

    // ---------------------------------------------------------------- core loop (spec 2.2)

    private fun onBubbleTapped() {
        val controller = overlay ?: return
        if (controller.isPanelOpen) {
            closePanel()
            return
        }
        // The same tap already closed the panel as an "outside" touch: don't reopen.
        if (SystemClock.uptimeMillis() - lastOutsideCloseAt < Config.PANEL_REOPEN_GUARD_MS) return
        if (currentApp == null) return

        try {
            controller.openPanel(panelCallbacks, panelBottomOffset())
        } catch (e: Exception) {
            DebugLog.error("Open panel", e)
            toast("Couldn't open ReplyMate panel")
            return
        }
        startSuggestionFlow()
    }

    /** Read the chat (fresh), then ask Gemini. Used for open, Regenerate and Retry. */
    private fun startSuggestionFlow() {
        val controller = overlay ?: return
        requestJob?.cancel()
        controller.renderPanel(PanelState.Loading)

        val conversation = readConversation() ?: panelConversation
        if (conversation == null) {
            controller.renderPanel(PanelState.Empty)
            return
        }
        panelConversation = conversation

        requestJob = scope?.launch {
            val settings = app.settings.settings.first()
            val result = app.suggester.suggest(settings, conversation, label = "Chat")
            if (overlay?.isPanelOpen == true) {
                overlay?.renderPanel(PanelState.fromResult(result, settings.exampleList.isNotEmpty()))
            }
        }
    }

    /** Reads the open chat; logs it, or auto-captures a node dump on failure (section 12). */
    private fun readConversation(): Conversation? {
        val app = currentApp ?: return null
        val snap = snapshotChatWindow()
        if (snap == null) {
            DebugLog.add(DebugType.ERROR, "Couldn't read this chat", "No chat window found.")
            return null
        }
        val screen = overlay?.screenSize() ?: return null
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
            return null
        }
        DebugLog.add(
            DebugType.CONVERSATION,
            "${app.name.lowercase().replaceFirstChar(Char::uppercase)} · ${conversation.messages.size} messages",
            conversation.describe(),
            conversation = conversation,
        )
        return conversation
    }

    /** Spec F6: put the text in the composer; clipboard if that fails. Never sends. */
    private fun insertSuggestion(s: Suggestion) {
        val controller = overlay ?: return
        val root = chatWindowRoot()
        val inserted = try {
            root != null && TextInserter.insert(root, s.text, controller.screenHeight())
        } finally {
            NodeUtils.recycle(root)
        }
        if (inserted) {
            DebugLog.add(DebugType.INFO, "Inserted ${s.tone} suggestion")
        } else {
            TextInserter.copyToClipboard(this, s.text)
            DebugLog.add(DebugType.INFO, "Insert failed: copied ${s.tone} suggestion to clipboard")
            toast("Copied — paste it in the chat")
        }
        closePanel()
    }

    private fun closePanel() {
        requestJob?.cancel() // spec F9: cancel the in-flight request when the panel closes
        requestJob = null
        panelConversation = null
        overlay?.closePanel()
    }

    private fun openApp(tab: String) {
        closePanel()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
        runCatching { startActivity(intent) }.onFailure { DebugLog.error("Open app", it) }
    }

    private val panelCallbacks = object : SuggestionPanelView.Callbacks {
        override fun onPick(suggestion: Suggestion) = insertSuggestion(suggestion)

        override fun onCopy(suggestion: Suggestion) {
            TextInserter.copyToClipboard(this@ReplyMateAccessibilityService, suggestion.text)
            toast("Copied")
        }

        override fun onRegenerate() = startSuggestionFlow()

        override fun onClose() = closePanel()

        override fun onAction(action: PanelAction) = when (action) {
            PanelAction.RETRY -> startSuggestionFlow()
            PanelAction.OPEN_SETTINGS -> openApp(MainActivity.TAB_PERSONA)
        }

        override fun onOpenDebug() = openApp(MainActivity.TAB_DEBUG)

        override fun onCapture() = captureNodeTree()

        override fun onOutsideTouch() {
            lastOutsideCloseAt = SystemClock.uptimeMillis()
            closePanel()
        }
    }

    private fun toast(text: String, long: Boolean = false) {
        Toast.makeText(this, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        closePanel()
        overlay?.onScreenChanged()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        closePanel()
        ServiceState.reset()
        DebugLog.clear() // spec section 9: debug entries are cleared on service stop
        overlay?.destroy()
        overlay = null
        scope?.cancel()
        scope = null
        super.onDestroy()
    }
}
