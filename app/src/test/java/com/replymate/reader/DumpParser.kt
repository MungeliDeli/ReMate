package com.replymate.reader

/**
 * Test helper: turns the Debug tab's node-dump text back into a [ScreenNode]
 * tree, so a dump shared from the phone can be dropped into
 * src/test/resources/dumps and used as a reader fixture.
 * Header lines (anything before the first indented-tree line) are skipped.
 */
object DumpParser {
    private val idRe = Regex("""\bid=(\S+)""")
    private val textRe = Regex("""\btext="([^"]*)"""")
    private val descRe = Regex("""\bdesc="([^"]*)"""")
    private val boundsRe = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

    private class Builder(val depth: Int, val node: ScreenNode) {
        val children = ArrayList<Builder>()
        fun build(): ScreenNode = node.copy(children = children.map { it.build() })
    }

    fun parse(dump: String, packageName: String? = null): ScreenNode {
        val lines = dump.lines()
            .dropWhile { !boundsRe.containsMatchIn(it) }
            .filter { boundsRe.containsMatchIn(it) }
        require(lines.isNotEmpty()) { "no node lines found" }
        val stack = ArrayList<Builder>()
        var root: Builder? = null
        for (line in lines) {
            val depth = (line.length - line.trimStart().length) / 2
            val b = Builder(depth, parseNode(line.trim(), packageName))
            while (stack.isNotEmpty() && stack.last().depth >= depth) stack.removeAt(stack.lastIndex)
            if (stack.isEmpty()) {
                check(root == null) { "more than one root" }
                root = b
            } else {
                stack.last().children += b
            }
            stack += b
        }
        return root!!.build()
    }

    private fun parseNode(s: String, pkg: String?): ScreenNode {
        val cls = s.substringBefore(' ')
        val m = boundsRe.find(s)!!.groupValues
        val flags = s.substring(boundsRe.find(s)!!.range.last + 1).split(' ').filter { it.isNotBlank() }.toSet()
        return ScreenNode(
            packageName = pkg,
            className = cls,
            viewId = idRe.find(s)?.groupValues?.get(1),
            text = textRe.find(s)?.groupValues?.get(1),
            contentDescription = descRe.find(s)?.groupValues?.get(1),
            bounds = Box(m[1].toInt(), m[2].toInt(), m[3].toInt(), m[4].toInt()),
            editable = "EDITABLE" in flags,
            clickable = "clickable" in flags,
            scrollable = "scrollable" in flags,
            visible = "hidden" !in flags,
        )
    }

    fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("dumps/$name")) { "missing fixture $name" }.readText()
}
