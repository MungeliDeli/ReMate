package com.replymate.debug

import com.replymate.reader.Box
import com.replymate.reader.DumpParser
import com.replymate.reader.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Test

class NodeDumpTest {
    private val tree = ScreenNode(
        className = "android.widget.FrameLayout",
        bounds = Box(0, 0, 1080, 2400),
        children = listOf(
            ScreenNode(
                className = "android.widget.TextView",
                viewId = "com.whatsapp:id/message_text",
                text = "hey",
                bounds = Box(60, 500, 620, 570),
            ),
            ScreenNode(
                className = "android.widget.EditText",
                viewId = "com.whatsapp:id/entry",
                bounds = Box(120, 2200, 880, 2300),
                editable = true,
                clickable = true,
            ),
            ScreenNode(className = "android.view.View", contentDescription = "Seen", visible = false),
        ),
    )

    @Test
    fun formatsIndentedLinesWithShortNames() {
        assertEquals(
            "FrameLayout [0,0][1080,2400]\n" +
                "  TextView id=message_text text=\"hey\" [60,500][620,570]\n" +
                "  EditText id=entry [120,2200][880,2300] EDITABLE clickable\n" +
                "  View desc=\"Seen\" [0,0][0,0] hidden\n",
            NodeDump.format(tree),
        )
    }

    @Test
    fun clipTruncatesTo60AndFlattensNewlines() {
        assertEquals("a".repeat(60) + "…", NodeDump.clip("a".repeat(100)))
        assertEquals("line1⏎line2 'q'", NodeDump.clip("line1\nline2 \"q\""))
    }

    @Test
    fun dumpRoundTripsThroughParser() {
        val parsed = DumpParser.parse("header line\n\n" + NodeDump.format(tree))
        assertEquals(NodeDump.format(tree), NodeDump.format(parsed))
        assertEquals("message_text", parsed.children[0].shortId)
        assertEquals(false, parsed.children[2].visible)
    }
}
