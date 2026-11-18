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
| M5 | Chat readers | ✅ Done |
| M6 | Gemini + prompt | ✅ Done |
| M7 | Suggestion panel + insert | ✅ Done |
| M8 | Polish | ⏳ Next |

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

### What M5 contains
- **Tap the bubble to read the chat.** ReplyMate reads the messages visible on screen (only when you tap) and saves the result in the Debug log. Nothing is sent anywhere yet.
- **Screen snapshot** ([ScreenNode.kt](app/src/main/java/com/replymate/reader/ScreenNode.kt)): ReplyMate copies the screen once, then reads from that copy. This avoids errors when the screen changes mid-read, and lets the readers be tested without a phone.
- **WhatsAppReader** ([WhatsAppReader.kt](app/src/main/java/com/replymate/reader/WhatsAppReader.kt)): finds message text by WhatsApp's view ID `message_text` and the contact name by `conversation_contact_name`. If WhatsApp renames these IDs, it falls back to reading all text inside the message list.
- **MessengerReader** ([MessengerReader.kt](app/src/main/java/com/replymate/reader/MessengerReader.kt)): Messenger has few stable IDs, so it reads the text (or descriptions) inside the biggest scrollable list. It skips buttons, "Seen" labels and timestamps. The contact name is the first text in the top 12% of the screen.
- **Shared rules** ([ChatReader.kt](app/src/main/java/com/replymate/reader/ChatReader.kt), [TextFilters.kt](app/src/main/java/com/replymate/reader/TextFilters.kt)):
  - Messages are put in top-to-bottom order; only the newest 15 are kept, each cut to 500 characters.
  - Date separators, "unread messages" banners, encryption and system notices, timestamps, Seen/Delivered labels and missed-call notices are skipped. Anything centred with a wide gap on both sides is treated as a date or system chip and skipped too.
  - **Who sent it:** a message whose middle is in the right half of the screen is ME; otherwise it's THEM. The spec suggests putting the line at 45% of the width; 50% is used because long incoming messages can reach past 45% and get mislabelled. You can change this in `Config.SENDER_SPLIT_RATIO`.
- **Debug tab:** a new "Last parsed conversation" card draws the result like a chat (yours on the right, theirs on the left), so mistakes are easy to spot. Each read also adds a **Conversation** entry to the log.
- **If nothing can be read**, ReplyMate shows "Couldn't read this chat" and saves a node dump of the screen automatically (spec section 12).
- **Tests** run the readers on sample screens in [src/test/resources/dumps](app/src/test/resources/dumps). Dumps shared from the phone can be dropped in as new test cases.

#### M5 tuning, round 1 (from real dumps, Pixel 6 / Android 17)
- **WhatsApp:**
  - A message counts as **yours when its row shows delivery ticks** (`status`); otherwise position decides.
  - The "centred date chip" check is no longer used on `message_text` rows. WhatsApp puts the time to the right of your text, so medium-length messages of yours looked centred and were dropped.
  - Quoted messages, the "Edited" label and stickers are ignored.
- **Messenger:**
  - Each real message reads `Name, message, double tap to see sent/receive date and time, double tap and hold to react on message`. When this format is on screen, only text in it is kept: the name and the "double tap…" ending are removed, and the name decides the sender (the contact's first name means THEM, anything else is ME).
  - That rule also drops quoted messages, "You replied to …" headers, time chips and "Delivered … ago" labels.
  - Older formats fall back to position.
- **New filters:** "Delivered/Seen/Sent … ago / by …", reply headers, and the new "secured with end-to-end encryption" notice.
- **Privacy:** test samples taken from real dumps keep only the layout. Names and message text are replaced, because this repository is public. **Never commit a raw dump.**

**Test M5 on the phone:**
1. Install the new APK and open a 1-to-1 **WhatsApp** chat with 10+ messages from both of you on screen.
2. **Tap the bubble.** A message says "Read N messages (X from you, Y from them)".
3. Open ReplyMate → **Debug** → "Last parsed conversation". Check that:
   - the messages are in the same order as on screen;
   - your messages are on the right (ME) and theirs on the left (THEM);
   - no dates, times, "Seen" or system notices slipped in;
   - the contact name is right.
4. Repeat in a **Messenger** chat.
5. If anything is wrong, **long-press the bubble** in that chat to capture a node dump, then **Share/Copy** it from Debug and paste it to Claude Code along with a note on what was wrong.

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

### What M6 contains
- **Redactor** ([Redactor.kt](app/src/main/java/com/replymate/ai/Redactor.kt)): before anything leaves the phone, it replaces:
  - emails with `[EMAIL]`;
  - links (http(s), www., and common domains such as .com and .co.zm) with `[LINK]`;
  - anything with 7 or more digits, allowing a leading +, spaces and dashes, with `[PHONE]` (for example `+260 97 123 4567`, `0971234567`, `097-123-4567`);
  - a plain run of 6 digits (codes, PINs) with `[NUMBER]`.

  Times, prices, years and short numbers are left alone.
- **PromptBuilder** ([PromptBuilder.kt](app/src/main/java/com/replymate/ai/PromptBuilder.kt)): builds the system instruction and conversation block exactly as in spec 8.2 and 8.3. Empty persona sections are left out. The other person is called "Them", or, with "Share first name with AI" on, only their redacted first name is used. If you sent the last message, it asks for follow-ups.
- **GeminiClient** ([GeminiClient.kt](app/src/main/java/com/replymate/ai/GeminiClient.kt)): plain REST with OkHttp, checked against ai.google.dev on 2026-11-18.
  - It calls `POST /v1beta/models/{model}:generateContent` and sends the key only in the `x-goog-api-key` header.
  - **Changed from the spec:** the JSON output schema goes in `responseJsonSchema`, because the spec's `responseSchema` is now documented as internal.
  - **Thinking:** it asks for `thinkingLevel: MINIMAL`, the cheapest and fastest level and the default for `gemini-3.5-flash-lite`. If a model rejects that level, it retries with `LOW`, then with no thinking setting.
  - **Timeouts:** 10 s to connect, 20 s to read.
  - **Retries:** one retry after 2 s on no connection, 429, 500 or 503. Closing the panel (M7) cancels the call.
  - **Errors** show the messages from spec section 12. Gemini answers a wrong key with a 400 error ("API key not valid"); that also shows "Your API key was rejected".
- **Response parsing** ([ResponseParser.kt](app/src/main/java/com/replymate/ai/ResponseParser.kt)): reads the JSON answer and takes the first 3 suggestions. If the answer isn't JSON, it falls back to one suggestion per line. A blocked prompt or safety stop shows "Gemini declined to suggest a reply for this chat".
- **Persona → Test:** sends a fixed sample chat with your *current* persona (even unsaved edits) and shows the 3 suggestions, each with its tone and the time taken.
- **Debug tab:** every call adds a **Request** entry (the full prompt, after redaction) and a **Response** or **Error** entry (HTTP status, time in ms, raw JSON). The API key is never in the prompt, and is masked as `****` if it ever appears.
- **Free tier note:** `gemini-3.5-flash-lite` is free of charge on the free tier, but on the free tier Google may use what you send to improve its products. The Redactor reduces what is shared, and turning on billing changes this.

**Test M6 on the phone:**
1. Install the new APK. On **Persona**, paste your **real** Gemini API key (from aistudio.google.com/apikey) and check your example messages are filled in.
2. Scroll to **Test** and tap **Test with sample chat**. Within a few seconds you should see 3 suggestions (CASUAL / WARM / SHORT) that sound like you.
3. Open **Debug** and check:
   - the **Request** entry shows `call me on [PHONE]`, not the number;
   - the request and response don't contain your API key.
4. Error checks:
   - change one character of the key and tap Test: "Your API key was rejected";
   - switch on airplane mode and tap Test: after a short pause, "No connection. Try again.";
   - set Model ID to `gemini-nope` and tap Test: "Model not found…";
   - afterwards, tap **Reset** on the Model ID and fix the key.
5. Try `gemini-3.8-flash` as the Model ID to compare quality. It should still work: it doesn't accept MINIMAL thinking, so the client falls back to LOW automatically.

### What M7 contains
- **The full loop:** tap the bubble in a chat. The panel slides up and ReplyMate reads the chat and asks Gemini. Then:
  - **tap a suggestion** to put it in the message box, and the panel closes; you still press Send yourself;
  - **long-press** a suggestion to copy it ("Copied").
- **Suggestion panel** ([SuggestionPanelView.kt](app/src/main/java/com/replymate/overlay/SuggestionPanelView.kt)): a bottom-sheet card, full width minus 12dp margins, at most 60% of the screen tall, in the spec 7 colours with dark mode.
  - It sits **just above the keyboard** when the keyboard is open, otherwise just above the navigation bar, and follows the keyboard as it opens and closes.
  - It never takes the keyboard away from WhatsApp or Messenger, and a tap outside it closes it.
  - **States:**
    - **Loading:** 3 pulsing placeholders and "Thinking…".
    - **Results:** 3 cards, each with a tone label and up to 6 lines of text.
    - **Error:** the spec section 12 message, plus **Retry**, or **Open settings** for key and model problems. A safety block has no retry.
    - **Empty:** "Couldn't read this chat", plus **Open debug**, and a node dump is saved automatically.
  - **Header:** **⋮** (capture screen for Debug / open debug), **Regenerate** (reads the chat again and asks for 3 new replies), and **Close**.
  - With no example messages in your persona, the results show "Add example messages for better replies".
- **TextInserter** ([TextInserter.kt](app/src/main/java/com/replymate/insert/TextInserter.kt)): finds the message box (WhatsApp by its view ID `entry`, otherwise the lowest text box) and **replaces** its text, with the cursor at the end. If that fails, it copies the reply instead and shows "Copied — paste it in the chat". **Nothing is ever sent.**
- **Closing the panel** (Close, outside tap, leaving the chat, rotating the screen) cancels any Gemini request still running and forgets the chat. The Debug log still keeps its in-memory entries.
- **Robustness:**
  - While you touch the panel, Android can briefly treat it as the active window. Reading and inserting therefore look for the chat app's own window, and the "still in a chat?" check ignores ReplyMate's overlays, but not the ReplyMate app itself.
  - Tapping the bubble while the panel is open closes it, without the same tap reopening it.

**Test M7 on the phone:**
1. Install the new APK and open a 1-to-1 **WhatsApp** chat.
2. **Tap the bubble.** The panel should slide up with "Thinking…", then show 3 suggestions within a few seconds.
3. **Tap one.** It should appear in the message box and the panel should close. **It must not be sent.** Edit it if you like, then send it yourself.
4. Open the panel again and **long-press** a card: you should see "Copied".
5. Tap **Regenerate**: new suggestions should appear. Tap outside the panel: it closes. Tap the bubble while the panel is open: it closes and doesn't reopen.
6. With the **keyboard open**, tap the bubble: the panel should sit above the keyboard.
7. Repeat steps 2–3 in **Messenger**.
8. Error checks:
   - **Airplane mode:** "No connection. Try again." with **Retry**.
   - **A wrong key on the Persona tab:** "Your API key was rejected" with **Open settings**, which opens ReplyMate on the Persona tab.
9. Leave the chat while the panel is open (Back / Home): the panel and bubble should disappear.
