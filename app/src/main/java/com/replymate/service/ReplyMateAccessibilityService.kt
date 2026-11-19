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
import com.replymate.ai.ReplyOptions
import com.replymate.ai.Suggestion
import com.replymate.reader.ChatHistory
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    /** Style picked in the panel for this reply. */
    private var panelOptions = ReplyOptions()
    private val scroller by lazy { ChatScroller(this) }
    /** One history read (scroll up, read, scroll back) at a time. */
    private val scrollMutex = Mutex()
    private var requestJob: Job? = null
    private var lastOutsideCloseAt = 0L
    /** True while scrolling/reading history: our own swipe must not close the panel. */
    private var readingHistory = false
    /** Screens read so far in the current history read (for the Debug log if cut short). */
    private var screensReadSoFar = 0

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
        // While we scroll the chat ourselves, the app may redraw and the composer can be
        // missed for a moment. Still in the chat app = still in the chat.
        if (detected == null && readingHistory && AppDetector.appFor(pkg) != null) return
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
            closePanel("left the chat screen") // spec 2.3: leaving the chat hides button and panel
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
            closePanel("bubble tapped")
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
        // Style picker first; the last choice is pre-selected.
        controller.renderPanel(PanelState.Options(panelOptions))
        scope?.launch {
            val last = app.settings.replyOptions.first()
            panelOptions = last
            if (overlay?.isPanelOpen == true) overlay?.renderPanel(PanelState.Options(last))
        }
    }

    /**
     * Read the chat (unless [reread] is false and it was already read while this
     * panel is open), then ask Gemini with the chosen style.
     * Used for Suggest, Regenerate and Retry.
     */
    private fun startSuggestionFlow(reread: Boolean) {
        val controller = overlay ?: return
        requestJob?.cancel()
        requestJob = scope?.launch {
            val settings = app.settings.settings.first()
            val cached = panelConversation
            val conversation = if (!reread && cached != null) cached else {
                controller.renderPanel(PanelState.Reading(settings.contextScreens))
                readWithHistory(settings.contextScreens)
            }
            if (overlay?.isPanelOpen != true) return@launch
            if (conversation == null) {
                controller.renderPanel(PanelState.Empty)
                return@launch
            }
            panelConversation = conversation
            controller.renderPanel(PanelState.Loading)
            val options = panelOptions
            val result = app.suggester.suggest(settings, conversation, label = "Chat", options = options)
            if (overlay?.isPanelOpen == true) {
                overlay?.renderPanel(
                    PanelState.fromResult(result, settings.exampleList.isNotEmpty(), options, conversation.messages.size),
                )
            }
        }
    }

    /**
     * Reads the visible chat, then (for [screens] > 1) scrolls up one page at a
     * time reading each, and always scrolls back down afterwards, even if the
     * panel is closed midway. Screens are joined without duplicates.
     * Logs the result; auto-captures a node dump if nothing can be read (section 12).
     */
    private suspend fun readWithHistory(screens: Int): Conversation? = scrollMutex.withLock {
        val app = currentApp ?: return null
        val screen = overlay?.screenSize() ?: return null
        val firstSnap = snapshotChatWindow()
        if (firstSnap == null) {
            DebugLog.add(DebugType.ERROR, "Couldn't read this chat", "No chat window found.")
            return null
        }
        val first = readSnapshot(app, firstSnap, screen.x, screen.y)
        if (first == null) {
            DebugLog.add(DebugType.ERROR, "Couldn't read this chat", "No messages found. A node dump was captured automatically.")
            captureNodeTree(existing = firstSnap, reason = "no messages read", showToast = false)
            return null
        }

        val pages = mutableListOf(first.messages) // oldest screen first
        var scrolls = 0
        var method: ChatScroller.Method? = null
        screensReadSoFar = 1
        if (screens > 1) {
            readingHistory = true
            overlay?.setPanelTouchThrough(true) // let the scroll reach the chat
            try {
                for (i in 2..screens) {
                    val root = chatWindowRoot() ?: break
                    val used = try {
                        scroller.scroll(root, older = true, screenWidth = screen.x, screenHeight = screen.y, prefer = method)
                    } finally {
                        NodeUtils.recycle(root)
                    }
                    if (used == null) break // top of the chat, or can't scroll here
                    method = used
                    scrolls++
                    delay(Config.SCROLL_SETTLE_MS)
                    val snap = snapshotChatWindow() ?: break
                    val page = readSnapshot(app, snap, screen.x, screen.y) ?: break
                    if (page.messages == pages.first()) break // didn't move
                    pages.add(0, page.messages)
                    screensReadSoFar = pages.size
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                DebugLog.error("Scroll for history", e)
            } finally {
                withContext(NonCancellable) {
                    // Swipes can stop a little short, so add one extra on the way back.
                    val back = if (method == ChatScroller.Method.SWIPE) scrolls + 1 else scrolls
                    repeat(back) {
                        val root = chatWindowRoot() ?: return@repeat
                        try {
                            scroller.scroll(root, older = false, screenWidth = screen.x, screenHeight = screen.y, prefer = method)
                        } finally {
                            NodeUtils.recycle(root)
                        }
                        delay(Config.SCROLL_SETTLE_MS / 2)
                    }
                    overlay?.setPanelTouchThrough(false)
                    readingHistory = false
                }
            }
        }

        val merged = ChatHistory.merge(pages, Config.MAX_CONTEXT_MESSAGES)
        val conversation = first.copy(messages = merged)
        DebugLog.add(
            DebugType.CONVERSATION,
            "${app.name.lowercase().replaceFirstChar(Char::uppercase)} · ${merged.size} messages " +
                "(${pages.size} screen${if (pages.size > 1) "s" else ""}${method?.let { ", scrolled by ${it.name.lowercase()}" } ?: ""})",
            conversation.describe(),
            conversation = conversation,
        )
        return conversation
    }

    private fun readSnapshot(app: SupportedApp, snap: ScreenNode, width: Int, height: Int): Conversation? {
        val reader = when (app) {
            SupportedApp.WHATSAPP -> WhatsAppReader
            SupportedApp.MESSENGER -> MessengerReader
        }
        return try {
            reader.readConversation(snap, width, height)
        } catch (e: Exception) {
            DebugLog.error("Read chat", e)
            null
        }
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
        closePanel("suggestion picked")
    }

    private fun closePanel(reason: String = "closed") {
        if (requestJob?.isActive == true) {
            // Make it visible in Debug when work is cut short (e.g. the panel closed while reading).
            DebugLog.add(
                DebugType.INFO,
                "Panel closed while working: $reason",
                if (readingHistory) "Cancelled while reading chat history after $screensReadSoFar screen(s); the chat was scrolled back." else "",
            )
        }
        requestJob?.cancel() // spec F9: cancel the in-flight request when the panel closes
        requestJob = null
        panelConversation = null
        overlay?.closePanel()
    }

    private fun openApp(tab: String) {
        closePanel("opened ReplyMate app")
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
        runCatching { startActivity(intent) }.onFailure { DebugLog.error("Open app", it) }
    }

    private val panelCallbacks = object : SuggestionPanelView.Callbacks {
        override fun onSuggest(options: ReplyOptions) {
            panelOptions = options
            app.appScope.launch { app.settings.saveReplyOptions(options) }
            startSuggestionFlow(reread = true)
        }

        override fun onShowOptions() {
            overlay?.renderPanel(PanelState.Options(panelOptions))
        }

        override fun onPick(suggestion: Suggestion) = insertSuggestion(suggestion)

        override fun onCopy(suggestion: Suggestion) {
            TextInserter.copyToClipboard(this@ReplyMateAccessibilityService, suggestion.text)
            toast("Copied")
        }

        // Reuse the chat already read while this panel is open: no second scroll.
        override fun onRegenerate() = startSuggestionFlow(reread = panelConversation == null)

        override fun onClose() = closePanel("close button")

        override fun onAction(action: PanelAction) = when (action) {
            PanelAction.RETRY -> startSuggestionFlow(reread = panelConversation == null)
            PanelAction.OPEN_SETTINGS -> openApp(MainActivity.TAB_PERSONA)
        }

        override fun onOpenDebug() = openApp(MainActivity.TAB_DEBUG)

        override fun onCapture() = captureNodeTree()

        override fun onOutsideTouch() {
            // Our own scroll swipe lands outside the panel: never treat it as the owner tapping away.
            if (readingHistory) return
            lastOutsideCloseAt = SystemClock.uptimeMillis()
            closePanel("tapped outside")
        }
    }

    private fun toast(text: String, long: Boolean = false) {
        Toast.makeText(this, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        closePanel("screen rotated or theme changed")
        overlay?.onScreenChanged()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        closePanel("service stopped")
        ServiceState.reset()
        DebugLog.clear() // spec section 9: debug entries are cleared on service stop
        overlay?.destroy()
        overlay = null
        scope?.cancel()
        scope = null
        super.onDestroy()
    }
}
