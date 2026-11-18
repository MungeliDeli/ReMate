# ReplyMate

A personal Android tool. When a WhatsApp or Messenger chat is open, a small floating button appears. Tap it and ReplyMate reads the recent messages, asks Google Gemini for **3 reply suggestions written in your own style**, and puts the one you pick into the chat box. **You always press Send yourself.**

Personal use only: it's installed as a sideloaded APK and is not on Google Play. The full design is in the project spec (v1).

## Status

| Milestone | What | State |
|---|---|---|
| M1 | Skeleton & build pipeline | ✅ Done |
| M2 | Settings & persona | ⏳ Next |
| M3 | Accessibility service + bubble | — |
| M4 | Debug screen & node dump | — |
| M5 | Chat readers | — |
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
