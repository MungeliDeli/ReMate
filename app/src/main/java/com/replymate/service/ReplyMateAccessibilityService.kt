package com.replymate.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.replymate.Config
import com.replymate.ReplyMateApplication
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
            onPositionChanged = { pos -> app.appScope.launch { app.settings.saveBubblePosition(pos) } },
        )
        overlay = controller
        serviceScope.launch {
            app.settings.bubblePosition.collect { controller.position = it }
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
        val detected = try {
            AppDetector.detect(root, controller.screenHeight())
        } catch (_: Exception) {
            null
        } finally {
            NodeUtils.recycle(root)
        }
        setChatApp(detected)
    }

    private fun recheckForeground() {
        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        val pkg = root?.packageName
        NodeUtils.recycle(root)
        emptyChecks = if (pkg == null) emptyChecks + 1 else 0
        val stillInChatApp = AppDetector.appFor(pkg) != null
        // A missing window is often a brief transition; only give up after two in a row.
        if (stillInChatApp || (pkg == null && emptyChecks < 2)) scheduleForegroundCheck() else setChatApp(null)
    }

    private fun setChatApp(detected: SupportedApp?) {
        val controller = overlay ?: return
        currentApp = detected
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

    private fun onBubbleTapped() {
        // M7 replaces this with the suggestion panel.
        val name = when (currentApp) {
            SupportedApp.WHATSAPP -> "WhatsApp"
            SupportedApp.MESSENGER -> "Messenger"
            null -> "a"
        }
        Toast.makeText(this, "ReplyMate sees $name chat. Suggestions arrive in a later update.", Toast.LENGTH_SHORT).show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onScreenChanged()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay?.destroy()
        overlay = null
        scope?.cancel()
        scope = null
        super.onDestroy()
    }
}
