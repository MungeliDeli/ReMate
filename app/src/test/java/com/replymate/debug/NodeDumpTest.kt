package com.replymate.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeDumpTest {
    private fun line(depth: Int, cls: String?, id: String? = null, text: String? = null, desc: String? = null, editable: Boolean = false) =
        DumpLine(depth, cls, id, text, desc, bounds = null, editable = editable, clickable = false, scrollable = false)

    @Test
    fun formatsIndentedLinesWithShortNames() {
        val out = NodeDump.format(
            listOf(
                line(0, "android.widget.FrameLayout"),
                line(1, "android.widget.TextView", id = "com.whatsapp:id/message_text", text = "hey"),
                line(1, "android.widget.EditText", id = "com.whatsapp:id/entry", editable = true),
            ),
        )
        assertEquals(
            "FrameLayout\n" +
                "  TextView id=message_text text=\"hey\"\n" +
                "  EditText id=entry EDITABLE\n",
            out,
        )
    }

    @Test
    fun clipTruncatesTo60AndFlattensNewlines() {
        val long = "a".repeat(100)
        assertEquals("a".repeat(60) + "…", NodeDump.clip(long))
        assertEquals("line1⏎line2 'q'", NodeDump.clip("line1\nline2 \"q\""))
    }

    @Test
    fun includesDescriptionWhenNoText() {
        val out = NodeDump.format(listOf(line(0, "android.view.View", desc = "Seen")))
        assertTrue(out.contains("desc=\"Seen\""))
    }
}
