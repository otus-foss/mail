package com.otus.mail.feature.folders.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlockSpliceTest {
    private val block = "# BEGIN otus-rules\r\nif true {\r\n    stop;\r\n}\r\n# END otus-rules\r\n"
    private val newer = "# BEGIN otus-rules\r\n# newer\r\n# END otus-rules\r\n"
    private val handWritten =
        listOf(
            "# My filters, written by hand.",
            "require [\"fileinto\", \"vacation\"];",
            "require \"envelope\"; # and a comment",
            "",
            "if header :contains \"subject\" \"; not a require\" {",
            "    fileinto \"Odd\";",
            "}",
            "vacation :days 3 \"Away\";  ",
        )

    @Test
    fun aMissingBlockGoesAfterTheLeadingRequiresAndNothingElseChanges() {
        listOf("\n", "\r\n").forEach { ending ->
            val script = handWritten.joinToString(ending, postfix = ending)
            val spliced = spliceBlock(script, block)
            val head = handWritten.take(3).joinToString(ending, postfix = ending)
            assertEquals(head + block + script.removePrefix(head), spliced)
            assertEquals(script, spliced.replace(block, ""), "Every byte outside the block stays")
        }
    }

    @Test
    fun aScriptWithoutRequiresGetsTheBlockAtTheTop() {
        val script = "# Only a comment\nkeep;\n"
        assertEquals(block + script, spliceBlock(script, block))
        assertEquals(block, spliceBlock("", block))
    }

    @Test
    fun aRequireOnTheLastLineWithoutALineEndGetsOne() {
        assertEquals("require \"body\";\r\n$block", spliceBlock("require \"body\";", block))
        assertEquals("require \"body\";\r\n${block}keep;", spliceBlock("require \"body\";keep;", block))
    }

    @Test
    fun anExistingBlockIsReplacedInPlace() {
        listOf("\n", "\r\n").forEach { ending ->
            val before = handWritten.take(3).joinToString(ending, postfix = ending)
            val after = handWritten.drop(3).joinToString(ending, postfix = ending)
            val lfBlock = block.replace("\r\n", ending)
            val spliced = spliceBlock(before + lfBlock + after, newer)
            assertEquals(before + newer + after, spliced)
            assertEquals(lfBlock, blockText(before + lfBlock + after))
        }
    }

    @Test
    fun aBlockWithoutItsEndLineIsNoBlock() {
        assertNull(findBlock("# BEGIN otus-rules\nkeep;\n"))
        assertEquals(BlockSpan(0, block.length), findBlock(block))
        assertEquals(BlockSpan(0, block.length - 2), findBlock(block.removeSuffix("\r\n")))
    }
}
