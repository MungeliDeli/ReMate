package com.replymate.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class RedactorTest {
    private fun r(s: String) = Redactor.redact(s)

    @Test
    fun zambianPhoneFormats() {
        assertEquals("call [PHONE] now", r("call +260 97 123 4567 now"))
        assertEquals("call [PHONE]", r("call 0971234567"))
        assertEquals("[PHONE] or [PHONE]", r("+260971234567 or 097-123-4567"))
        assertEquals("my number is [PHONE].", r("my number is 0961 234 567."))
    }

    @Test
    fun otherPhonesAndCodes() {
        assertEquals("[PHONE]", r("+44 20 7946 0958"))
        assertEquals("code [NUMBER]", r("code 482913"))
        assertEquals("acc [PHONE]", r("acc 12345678901")) // 7+ digits always redacted
    }

    @Test
    fun emails() {
        assertEquals("mail me at [EMAIL] ok", r("mail me at sam.banda+work@gmail.com ok"))
    }

    @Test
    fun links() {
        assertEquals("see [LINK]", r("see https://example.com/a?b=1"))
        assertEquals("see [LINK] now", r("see www.zambia.co.zm/news now"))
        assertEquals("try [LINK]", r("try shop.example.com/item/42"))
        assertEquals("[LINK]", r("http://192.168.0.1:8080/x"))
    }

    @Test
    fun leavesNormalTextNumbersAlone() {
        listOf(
            "see you at 18:30", "K1,500 only", "back in 2026", "10 - 20 people", "room 12345",
            "call me lol", "it's 3am", "pin is 12", "version 2.4.21",
        ).forEach { assertEquals(it, r(it)) }
    }

    @Test
    fun mixed() {
        assertEquals(
            "send [LINK] to [EMAIL] then call [PHONE], OTP [NUMBER]",
            r("send https://x.io/p to a@b.com then call 0971234567, OTP 123456"),
        )
    }
}
