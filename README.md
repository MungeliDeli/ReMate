# ReplyMate

A personal Android tool. When a WhatsApp or Messenger chat is open, a small floating button appears. Tap it and ReplyMate reads the recent messages, asks Google Gemini for **3 reply suggestions written in your own style**, and puts the one you pick into the chat box. **You always press Send yourself.**

Personal use only: it's installed as a sideloaded APK and is not on Google Play. The full design is in the project spec (v1).

## Status

| Milestone | What | State |
|---|---|---|
| M1 | Skeleton & build pipeline | ✅ Done |
| M2 | Settings & persona | ✅ Done |
| M3 | Accessibility service + bubble | ✅ Done |
| M4 | Debug screen & node dump | ✅ Done |
| M5 | Chat readers | ⏳ Next |
| M6 | Gemini + prompt | — |
| M7 | Suggestion panel + insert | — |
| M8 | Polish | — |

### What M1 contains
- Gradle (Kotlin DSL) project with a version catalog ([gradle/libs.versions.toml](gradle/libs.versions.toml)) and a single `:app` module.
- `MainActivity` (Jetpack Compose, Material 3) with three bottom tabs: **Setup**, **Persona**, **Debug**. The tabs are placeholders for now.
- ReplyMate teal theme with dark mode that follows the system.
- A Codespaces dev container that installs the Android SDK.
- A GitHub Actions workflow that runs the unit tests, builds the APK and uploads it.
- A fixed debug signing key, so new builds install over old ones.

**Test M1 on the phone:** install the APK (see below), open ReplyMate, and switch between the three tabs. Then switch the phone to dark mode; the app should follow.

### What M2 contains
- **Settings storage** ([SettingsRepository.kt](app/src/main/java/com/replymate/data/SettingsRepository.kt)): saved in app-private Preferences DataStore and excluded from backups.
- **Persona tab:** name, about me (500 characters max), writing style, example messages (one per line, max 40), things to avoid, reply language, Gemini API key (hidden unless you tap Show), Model ID (default `gemini-3.5-flash-lite`, with Reset), and a "Share first name with AI" switch (off by default). Changes save automatically; the top-right corner shows "Saving…" then "Saved".
- **Setup tab checklist:** API key, persona (your name plus at least 5 example messages), and the accessibility service. Each item gets a green tick when it's done, and the service status is re-checked whenever you come back to the app. On Android 13+ it also explains how to "Allow restricted settings".
- The Persona **Test** button arrives in M6, together with the Gemini connection.

**Test M2 on the phone:**
1. Open **Setup**. It should say "0 of 3 done".
2. Tap **Add API key**, which takes you to Persona. Paste any text as the key for now (it isn't checked until M6), fill in your name and add 5+ example messages.
3. Go back to **Setup**: items 1 and 2 should now have green ticks.
4. Fully close ReplyMate (swipe it away from recent apps) and reopen it. Everything should still be filled in.
5. Tap **Enable service**. The Accessibility settings page should open. (In M2 ReplyMate wasn't in that list yet; it appears from M3 on.)

### What M3 contains
- **Accessibility service** ([ReplyMateAccessibilityService.kt](app/src/main/java/com/replymate/service/ReplyMateAccessibilityService.kt)): it only listens to WhatsApp, WhatsApp Business and Messenger. In this milestone it **doesn't read any messages**; it only decides whether to show the bubble.
- **Chat detection** ([AppDetector.kt](app/src/main/java/com/replymate/service/AppDetector.kt)): a screen counts as a chat when it belongs to one of those apps **and** has a message box in the lower part of the screen. For WhatsApp the box is found by its view ID `entry`. This rules out the chat list, Status/Stories and Settings.
- **Leaving a chat:** the spec's config only sends ReplyMate events from the three chat apps, so it never hears about you switching to another app. While the bubble is visible, the service therefore checks once a second which app is in front (the app name only, never screen content) and hides the bubble when that's no longer a chat app.
- **Floating bubble:** a 52dp teal circle with 85% opacity at rest, 100% while pressed, and a 150 ms fade in and out. Its default spot is the right edge, 40% down the screen. You can drag it anywhere; when you let go it snaps to the nearest side, and that position is remembered. Tapping it gives a short vibration; for now it only shows a toast (the suggestion panel comes in M7).
- All tunable numbers are in [Config.kt](app/src/main/java/com/replymate/Config.kt).

**Test M3 on the phone:**
1. Install the new APK over the old one.
2. On **Setup**, tap **Enable service**, find **ReplyMate** in the list and switch it on. On Android 13+, if it's greyed out, follow the "Allow restricted settings" steps shown in the app. Come back to ReplyMate: item 3 should have a green tick.
3. Open WhatsApp and the **chat list**: no bubble.
4. Open a **chat**: the bubble fades in on the right edge. Tap it: you should feel a short vibration and see "ReplyMate sees WhatsApp chat…".
5. Go back to the chat list: the bubble fades out. Open Status or Settings in WhatsApp: no bubble.
6. In a chat, drag the bubble to the left side and let go: it snaps to the edge. Leave the chat, come back: it's still on the left.
7. Press Home or switch to another app: the bubble disappears within about a second.
8. Repeat steps 3–5 in **Messenger** (and WhatsApp Business if you use it).
9. If the bubble shows where it shouldn't (or not in a chat), note which screen it happened on. The M4 Debug tab lets us capture what ReplyMate sees there.

### What M4 contains
- **Debug log** ([DebugLog.kt](app/src/main/java/com/replymate/debug/DebugLog.kt)): keeps the last 50 entries in memory only. It's never written to disk or Logcat, and it's cleared when the service stops or you tap **Clear**. Entry types are node dump, conversation, request, response, error and info; later milestones fill in the others.
- **Node-tree capture** ([NodeDump.kt](app/src/main/java/com/replymate/debug/NodeDump.kt)): records every element on the screen, one indented line each, with its class, view ID, text (cut to 60 characters), description, position on screen, and editable/clickable/scrollable flags. A header line notes the app, screen size, Android version and phone model. There are two ways to capture:
  - **Long-press the bubble** in a chat. The spec puts this in the suggestion panel's menu; that's added in M7.
  - **Capture in 5 seconds** on the Debug tab, then switch to the screen you want. Use this for screens where the bubble doesn't show.
- **Debug tab:** shows the service status (enabled in settings, running, last app seen, chat detected), the capture button, and the log. Tap an entry to expand it. Each entry has **Share** (opens Android's share menu) and **Copy**.
- Tapping the bubble still only shows a toast; the suggestion panel comes in M7.

> A node dump includes the visible text of the chat (each piece cut to 60 characters). It stays on the phone unless you share it yourself.

**Test M4 on the phone:**
1. Install the new APK and open the **Debug** tab. "Enabled in settings" and "Running" should both say **Yes**.
2. Open a WhatsApp chat and **long-press the bubble** for about half a second. You should feel a stronger vibration and see "Captured N nodes…".
3. Back in ReplyMate's **Debug** tab, a **Node dump** entry appears. Tap it to expand, then try **Share** and **Copy**.
4. Check the status card: "Last app seen" should be `com.whatsapp` and "Chat detected" should say Yes.
5. Do the same in a **Messenger** chat.
6. Tap **Capture in 5 seconds**, switch to the WhatsApp chat list, and wait. A new dump of that screen should appear.
7. **For M5:** share one WhatsApp chat dump and one Messenger chat dump with Claude Code (paste the text). Use a 1-to-1 chat with **10 or more messages from both people** visible on screen.

## Building

### In GitHub Codespaces
Open the repo in a Codespace (4-core recommended). The dev container installs the Android SDK automatically through [.devcontainer/install-android-sdk.sh](.devcontainer/install-android-sdk.sh). If you're in a Codespace without the dev container, run that script by hand once.

```bash
./gradlew testDebugUnitTest     # unit tests
./gradlew assembleDebug         # -> app/build/outputs/apk/debug/app-debug.apk
```

### In GitHub Actions
Every push to `main` (or a manual run from the **Actions** tab) runs the tests and builds the APK.
- Open the run and download the **replymate-debug-apk** artifact. It's a zip containing `app-debug.apk`.
- To get a direct phone-friendly download, push a tag instead: `git tag v0.1.0 && git push origin v0.1.0`. The APK is then attached to a GitHub Release for that tag.

## Installing on the phone
1. Download `app-debug.apk` on the phone (from the Release page, or unzip the Actions artifact).
2. Open it. When Android asks, allow **Install unknown apps** for your browser or Files app.
3. Install. Later versions install over the old one without uninstalling, because every build is signed with the same key ([keystore/debug.keystore](keystore/debug.keystore)). This keeps the accessibility permission from being lost on each update.

> The committed keystore is a **debug-only** key with the public password `android`. Never use it to sign anything you publish.

## Tech
Kotlin · Jetpack Compose + Material 3 · AGP 9.4.1 (built-in Kotlin) · Kotlin 2.4.21 · Gradle 9.8.2 · minSdk 26 · target/compileSdk 37 · JDK 17.

## Privacy rules (hard requirements)
- Nothing is ever sent automatically. No code path presses Send.
- No chat content is stored on disk.
- The Gemini API key is entered inside the app. It is never committed to this repo.
