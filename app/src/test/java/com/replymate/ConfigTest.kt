package com.replymate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    @Test
    fun supportsExactlyTheThreeTargetApps() {
        assertEquals(3, Config.SUPPORTED_PACKAGES.size)
        assertTrue("com.whatsapp" in Config.SUPPORTED_PACKAGES)
        assertTrue("com.whatsapp.w4b" in Config.SUPPORTED_PACKAGES)
        assertTrue("com.facebook.orca" in Config.SUPPORTED_PACKAGES)
    }
}
