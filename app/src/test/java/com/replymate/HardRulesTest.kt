package com.replymate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the spec's hard requirements (sections 1.4, 9, 14, 16) by scanning the
 * app source. If one of these fails, a change has broken a privacy/safety rule.
 */
class HardRulesTest {
    private val mainDir = File("src/main")
    private val sources: Map<String, String> by lazy {
        mainDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .associate { it.path to it.readText() }
    }
    private val kotlin get() = sources.filterKeys { it.endsWith(".kt") }

    private fun offenders(pattern: Regex, files: Map<String, String> = kotlin) =
        files.flatMap { (path, text) ->
            text.lines().mapIndexedNotNull { i, line -> if (pattern.containsMatchIn(line)) "$path:${i + 1}: ${line.trim()}" else null }
        }

    @Test
    fun sourcesFound() = assertTrue("run from the app module", kotlin.size > 20)

    @Test
    fun neverClicksOrGesturesInOtherApps() {
        // No click on another app's node (e.g. Send), no global actions, no synthetic taps.
        assertEquals(emptyList<String>(), offenders(Regex("""ACTION_CLICK|ACTION_LONG_CLICK|performGlobalAction|dispatchGesture""")))
    }

    @Test
    fun onlyNodeActionsAreSetTextAndSelection() {
        val actions = offenders(Regex("""\.performAction\("""))
        assertTrue("TextInserter must set text", actions.any { "ACTION_SET_TEXT" in it })
        actions.forEach { assertTrue("unexpected node action: $it", "ACTION_SET_TEXT" in it || "ACTION_SET_SELECTION" in it) }
    }

    @Test
    fun noLogcat() {
        assertEquals(emptyList<String>(), offenders(Regex("""android\.util\.Log\b|\bLog\.[vdiwe]\(|println\(""")))
    }

    @Test
    fun noChatContentWrittenToDisk() {
        // Settings go to DataStore only; nothing else may write files.
        assertEquals(
            emptyList<String>(),
            offenders(Regex("""FileOutputStream|FileWriter|openFileOutput|\.writeText\(|\.writeBytes\(|getSharedPreferences|SQLite|Room\b""")),
        )
    }

    @Test
    fun apiKeyNeverInBuildConfig() {
        val gradle = File("build.gradle.kts").readText()
        assertFalse(gradle.contains("buildConfigField"))
        assertFalse(gradle.contains("AIza"))
        assertEquals(emptyList<String>(), offenders(Regex("""AIza[0-9A-Za-z_\-]{20,}"""), sources))
    }

    @Test
    fun manifestOnlyInternetAndNoBackup() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val perms = Regex("""uses-permission\s+android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.permission.INTERNET"), perms)
        assertTrue(manifest.contains("""android:allowBackup="false""""))
    }

    @Test
    fun committedTestDumpsAreSynthesisedOrAnonymised() {
        // The repo is public: raw dumps from the phone must never be committed.
        File("src/test/resources/dumps").listFiles().orEmpty().forEach { f ->
            val head = f.readLines().take(4).joinToString("\n")
            assertTrue("${f.name} must say it is synthetic or anonymised", head.contains("Synthetic") || head.contains("replaced"))
        }
    }
}
