# ReplyMate

A personal Android tool. When a WhatsApp or Messenger chat is open, a small teal bubble sits at the edge of the screen. Tap it and ReplyMate reads the recent messages, asks Google Gemini for **3 reply suggestions written in your own style**, and puts the one you pick into the chat box. **You always press Send yourself; ReplyMate never sends anything.**

- **Personal use only:** it's installed as a sideloaded APK and is not on Google Play.
- **Phones:** Android 8.0+ (API 26+).
- **Apps:** WhatsApp, WhatsApp Business, Messenger.
- **Version:** 1.1.0. This is spec v1 plus reply styles and up to 3 screens of chat history.

---

## 1. Install the app

1. On your phone, open this repo on GitHub and get the APK in one of two ways:
   - **Actions** tab → latest green ✓ **Build APK** run → download **replymate-debug-apk**. It's a zip, so unzip it to get `app-debug.apk`.
   - **Releases** page (only if a `v*` tag was pushed) → download `app-debug.apk` directly.
2. Open the APK. When Android asks, allow **Install unknown apps** for your browser or Files app, then tap **Install**.
3. **Updates** install over the old version; you never need to uninstall. Every build is signed with the same key ([keystore/debug.keystore](keystore/debug.keystore)), so your settings and the accessibility permission are kept.

## 2. One-time setup (the Setup tab)

Open **ReplyMate**. The **Setup** tab shows a checklist; each item gets a green tick when it's done.

1. **Gemini API key**
   - Tap **Get a key** (opens aistudio.google.com/apikey) and create a free key.
   - Tap **Add API key**, which takes you to the Persona tab, and paste the key into **Gemini API key**.
2. **Your persona** (Persona tab). Fill in:
   - **Your first name.**
   - **About me:** age range, vibe, interests (500 characters max).
   - **Writing style:** e.g. "short messages, lowercase, light slang, occasional emoji".
   - **Example messages**, one per line. **This matters most.** Paste 15–20 messages you've really sent (max 40). The tick needs your name and at least 5.
   - **Things to avoid**, and **Reply language** (Match the chat / English / Mixed).
   - **Chat context:** how much of the chat to read. Choose **This screen**, **2 screens** or **3 screens** (the default). For 2–3 screens, ReplyMate scrolls the chat up and back down when you ask for suggestions.

   Then scroll down and tap **Test with sample chat**. Within a few seconds you should see 3 suggestions labelled CASUAL / WARM / SHORT. If they don't sound like you, improve the example messages.
3. **Accessibility service**
   - Tap **Enable service**, find **ReplyMate** in the list and switch it on.
   - **Android 13+:** if ReplyMate is greyed out or blocked, Android is protecting you from a sideloaded app. Tap **Open App info** → **⋮** menu (top right) → **Allow restricted settings**, then go back and tap **Enable service** again.
4. **Tip: battery.** Tap **Open App info** → **App battery usage** (or **Battery**) → **Unrestricted** (or turn on **Allow background usage**). Otherwise Android may stop ReplyMate and the bubble stops appearing.

When all three items have ticks, the screen says **"All set"**.

## 3. Daily use

1. Open a chat in WhatsApp or Messenger. The **bubble** fades in at the screen edge. It only shows on chat screens, not the chat list, Status or Settings.
2. **Tap the bubble.** A panel slides up with **style options**, and your last choice is pre-selected. You can combine them:
   - **Mood** (up to 2): Light · Flirty · Romantic · Sentimental · Comforting (for when they're sad)
   - **Length:** Short · Medium · Long
   - **Vibe:** Chilled · Funny · Playful · Serious

   Leave everything unpicked for the classic casual / warm / short trio. Then tap **Suggest**. ReplyMate reads the chat (scrolling up 1–2 screens and back, depending on Chat context), thinks, and shows **3 suggestions**. They all follow your style but each takes a different angle (e.g. TEASING / SMOOTH / BOLD). Your persona stays the same; the style only changes the mood of this reply.
3. **Tap a suggestion** and it's placed in the message box. Edit it if you want, then **press Send yourself**. **Long-press** a suggestion to copy it instead.
4. Under the suggestions, **Change style** takes you back to the options. In the panel header:
   - **↻ Regenerate** gets 3 new suggestions with the same style, using the chat already read (no second scroll);
   - **✕** closes the panel;
   - **⋮** captures the screen for Debug, or opens Debug.

   Tapping outside the panel also closes it.
5. **Drag the bubble** anywhere; it snaps to the nearest side and remembers where you left it. **Long-press the bubble** to capture the screen for the Debug tab.

## 4. When something goes wrong

| You see | What it means / what to do |
|---|---|
| "Add your Gemini API key in ReplyMate settings" | No key yet. Tap **Open settings** and paste it on the Persona tab. |
| "Add example messages for better replies" | Works, but replies won't sound like you. Add example messages. |
| "Couldn't read this chat" | ReplyMate couldn't find messages on this screen. A screen capture was saved: tap **Open debug** and share it (see below). |
| "No connection. Try again." | No internet or the request timed out (it already retried once). Tap **Retry**. |
| "Your API key was rejected" | Wrong or disabled key. Tap **Open settings** and fix it. |
| "Gemini is busy or the free limit was reached. Try again shortly." | Too many requests (it already retried once). Wait a bit. |
| "Gemini declined to suggest a reply for this chat" | Gemini's safety filter blocked this chat. There's no retry. |
| "Model not found…" | The Model ID on the Persona tab is wrong. Tap **Reset** next to it. |
| "Copied — paste it in the chat" | The message box couldn't be filled automatically. Long-press the box and paste. |
| The bubble stopped appearing | Android stopped the service. The Setup tab shows "Switched on, but not running". Turn ReplyMate off and on in Accessibility, and set battery use to **Unrestricted**. |

### The Debug tab
- **Service status:** whether the service is enabled and running, the last app it saw, and whether a chat is detected.
- **Last parsed conversation:** the chat as ReplyMate read it, with yours on the right and theirs on the left. Use it to spot reading mistakes.
- **Log** (last 50 entries, in memory only):
  - **Request:** the exact prompt sent, after redaction;
  - **Response:** HTTP status, time and raw answer;
  - **Error**, **Node dump** (screen capture) and **Conversation** entries.
- Each entry has **Share** and **Copy**.
- **To report a reading problem:** in that chat, long-press the bubble, then Share or Copy the **Node dump** entry and paste it to Claude Code with a note on what was wrong.

## 5. Privacy and safety (hard rules)

- **Nothing is ever sent automatically.** The only things ReplyMate does in other apps are:
  - set the message box text and move the cursor;
  - **scroll the chat** up and back down, to read more history.

  It never clicks or taps anything. Scrolling uses the list's own scroll action (WhatsApp), or a straight vertical swipe in the message area (Messenger, which offers no scroll action). Unit tests fail if anyone adds a click, or a gesture that isn't a vertical swipe.
- **It only reads chats when you tap Suggest**: the visible messages, plus up to 2 screens above when Chat context is 2–3 screens. That's at most 40 messages, each cut to 500 characters. The chat is always scrolled back down afterwards, even if you close the panel midway.
- **Personal data is redacted before anything leaves the phone:**
  - phone numbers become `[PHONE]` (`+260 97 123 4567`, `0971234567`…);
  - emails become `[EMAIL]` and links become `[LINK]`;
  - 6-digit codes become `[NUMBER]`.

  The other person is called "Them", unless you switch on **Share first name with AI**; then only their first name is sent.
- **No chat content is stored on disk.** The chat, prompt and response live in memory and are dropped when the panel closes. The Debug log is memory-only and cleared when the service stops or when you tap **Clear**. Nothing is written to Logcat.
- **The API key** is stored only on the phone (app-private DataStore, excluded from backups). It is never in this repo, never in the Debug log (masked as `****`), and never in a URL.
- **Gemini free tier:** Google may use what you send to improve its products. Redaction limits what it sees, and turning on billing changes this.
- **This repository is public.** Never commit a raw node dump from the phone. Test fixtures keep only the layout, with names and text replaced; a unit test checks this.

---

## For developers

### Build
- **Codespaces:** open the repo in a Codespace (4-core recommended). The dev container installs the Android SDK through [.devcontainer/install-android-sdk.sh](.devcontainer/install-android-sdk.sh); run that script by hand in any other environment.
  ```bash
  ./gradlew testDebugUnitTest     # unit tests
  ./gradlew assembleDebug         # -> app/build/outputs/apk/debug/app-debug.apk
  ```
- **GitHub Actions** ([build-apk.yml](.github/workflows/build-apk.yml)): every push to `main`, or a manual run, runs the tests, builds the APK and uploads it as **replymate-debug-apk**. Pushing a `v*` tag also attaches the APK to a GitHub Release.

The committed keystore is a **debug-only** key with the public password `android`. Never use it to sign anything you publish.

### Tech
Kotlin · Jetpack Compose + Material 3 (app screens) · classic Views (overlay) · AccessibilityService · OkHttp + kotlinx.serialization · Preferences DataStore · JUnit + MockWebServer.
AGP 9.4.1 (built-in Kotlin) · Kotlin 2.4.21 · Gradle 9.8.2 · minSdk 26 · target/compileSdk 37 · JDK 17.

### How it works
```
ReplyMateAccessibilityService
  AppDetector ──► chat screen? ──► OverlayController (bubble + panel)
  bubble tap ──► ScreenSnapshot ──► WhatsAppReader / MessengerReader ──► Conversation
             ──► Redactor ──► PromptBuilder ──► GeminiClient ──► 3 suggestions ──► panel
  card tap   ──► TextInserter (ACTION_SET_TEXT, clipboard fallback)
SettingsRepository (DataStore) · DebugLog (memory) · MainActivity: Setup / Persona / Debug
```

| Area | Files | Notes |
|---|---|---|
| Config | [Config.kt](app/src/main/java/com/replymate/Config.kt) | **Every tunable number**: sender split, max messages, timeouts, model ID… |
| Service and detection | [service/](app/src/main/java/com/replymate/service/) | A chat screen means a supported app **plus** a message box in the lower part of the screen. Events are debounced. While the bubble shows, a once-a-second check of which app is in front hides it when you leave. |
| History | [service/ChatScroller.kt](app/src/main/java/com/replymate/service/ChatScroller.kt), [reader/ChatHistory.kt](app/src/main/java/com/replymate/reader/ChatHistory.kt), [reader/SwipePlan.kt](app/src/main/java/com/replymate/reader/SwipePlan.kt) | Read a screen, scroll one page up (WhatsApp: `ACTION_SCROLL_BACKWARD`; Messenger: a vertical swipe that holds still at the end so the list doesn't fling), read again, then scroll back. While scrolling, the panel lets touches pass through. Screens are joined by removing the overlap. |
| Reply styles | [ai/ReplyOptions.kt](app/src/main/java/com/replymate/ai/ReplyOptions.kt) | Mood (up to 2) / Length / Vibe. The prompt swaps the casual/warm/short rule for the requested style; the length choice replaces the "max 3 sentences" rule; the persona sections are unchanged. |
| Readers | [reader/](app/src/main/java/com/replymate/reader/) | Work on a frozen `ScreenNode` copy of the screen, so they're unit-testable. **WhatsApp:** `message_text` IDs; delivery ticks (`status`) mean ME, otherwise position. **Messenger:** parses the "Name, message, double tap to see…" accessibility text; the name decides the sender; quotes, headers and receipts are dropped; position is the fallback. |
| AI | [ai/](app/src/main/java/com/replymate/ai/) | Redactor, PromptBuilder (spec 8.2/8.3), GeminiClient (REST, see below), ResponseParser, ReplySuggester (logs to Debug, masks the key). |
| Overlay | [overlay/](app/src/main/java/com/replymate/overlay/) | Bubble (52dp, draggable, snaps to the nearest side) and panel (above the keyboard; loading/results/error/empty states). |
| Insert | [insert/TextInserter.kt](app/src/main/java/com/replymate/insert/TextInserter.kt) | Sets the composer text; clipboard fallback. |

**Gemini API**, verified at ai.google.dev on 2026-11-18:
- `POST /v1beta/models/{model}:generateContent`, with the key in the `x-goog-api-key` header.
- Structured output uses `responseMimeType: application/json` and **`responseJsonSchema`**. The spec's `responseSchema` is now documented as internal.
- `thinkingConfig.thinkingLevel: MINIMAL`, the default for `gemini-3.5-flash-lite`. If a model rejects it, the client falls back to `LOW`, then to no thinking config.
- A wrong key returns `400 API_KEY_INVALID`, which is treated as "key rejected".

The default model `gemini-3.5-flash-lite` can be changed on the Persona tab without rebuilding (e.g. `gemini-3.8-flash`).

### Tests
`./gradlew testDebugUnitTest`, about 80 tests:
- Redactor, including Zambian phone formats;
- PromptBuilder and response parsing;
- GeminiClient against a local fake server (retries, auth, thinking fallback, safety);
- the readers, on synthetic and anonymised real layouts in [src/test/resources/dumps](app/src/test/resources/dumps);
- bubble geometry, the Debug log, panel states;
- [HardRulesTest](app/src/test/java/com/replymate/HardRulesTest.kt), which fails the build if the code ever clicks in another app, scrolls or gestures anywhere except ChatScroller (and there only as a vertical swipe), logs to Logcat, writes files, embeds an API key, adds a permission, enables backup, or commits a raw dump.

**Tuning a reader from a phone dump:** paste the dump into `src/test/resources/dumps/`, replacing names and message text but keeping positions and IDs. Add a header line saying the names were "replaced", then write a test like [RealLayoutTest](app/src/test/java/com/replymate/reader/RealLayoutTest.kt).

### Milestones
| | What | State |
|---|---|---|
| M1 | Skeleton & build pipeline | ✅ |
| M2 | Settings & persona | ✅ |
| M3 | Accessibility service + bubble | ✅ |
| M4 | Debug screen & node dump | ✅ |
| M5 | Chat readers (tuned on real WhatsApp + Messenger dumps) | ✅ |
| M6 | Gemini + prompt | ✅ |
| M7 | Suggestion panel + insert | ✅ |
| M8 | Polish: running/battery check, hard-rule tests, this guide | ✅ |
| 1.1 | Reply styles (mood / length / vibe) + 2–3 screens of chat history | ✅ |

### v1 acceptance checklist (spec section 14)
| Item | How it's covered |
|---|---|
| Installs from the Actions APK; updates install over the old version | CI artifact plus the fixed debug keystore |
| Setup guides you through the key, persona and accessibility (incl. restricted settings) | Setup tab |
| Bubble only on WhatsApp / WhatsApp Business / Messenger chat screens | AppDetector plus the foreground check; confirmed on the phone (M3) |
| Suggestions in ~5 s | About 5–6 s measured on the phone; the owner accepts up to 10 s |
| Suggestions reflect the persona | Persona Test plus real chats (confirmed on the phone, M6/M7) |
| Tapping fills the composer; nothing is sent automatically | TextInserter; `HardRulesTest` forbids clicks and gestures |
| Phones, emails and links redacted (visible in Debug) | Redactor; `ReplySuggesterTest` checks the Debug request |
| No chat content on disk; API key never in logs or Debug | `HardRulesTest` and `ReplySuggesterTest` |
| Every section 12 error shows the specified message | `PanelStateTest`, `GeminiClientTest` |
| Unit tests pass in CI | GitHub Actions |

### Roadmap (not built yet)
- **v1.1:** per-contact notes; custom tones; "shorter / funnier" buttons.
- **v1.2:** learn from the suggestions you pick.
- **v1.3:** group chats; replying to a specific message; summarising even longer histories (2–3 screens is done).
- **v2:** more apps; OCR fallback; on-device model.
