package com.replymate.service

import com.replymate.reader.NodeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDetectorTest {
    @Test
    fun mapsSupportedPackages() {
        assertEquals(SupportedApp.WHATSAPP, AppDetector.appFor("com.whatsapp"))
        assertEquals(SupportedApp.WHATSAPP, AppDetector.appFor("com.whatsapp.w4b"))
        assertEquals(SupportedApp.MESSENGER, AppDetector.appFor("com.facebook.orca"))
    }

    @Test
    fun ignoresOtherPackages() {
        assertNull(AppDetector.appFor(null))
        assertNull(AppDetector.appFor("com.facebook.katana"))
        assertNull(AppDetector.appFor("com.google.android.apps.nexuslauncher"))
        assertNull(AppDetector.appFor("com.replymate"))
    }

    @Test
    fun composerMustBeInLowerPartOfScreen() {
        val h = 2400
        assertFalse("top search bar", NodeUtils.isComposerPosition(150, h))
        assertTrue("composer above keyboard", NodeUtils.isComposerPosition(1200, h))
        assertTrue("composer at bottom", NodeUtils.isComposerPosition(2250, h))
        assertFalse("unknown screen", NodeUtils.isComposerPosition(500, 0))
    }
}
