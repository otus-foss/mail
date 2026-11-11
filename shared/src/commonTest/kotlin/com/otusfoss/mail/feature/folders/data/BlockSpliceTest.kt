package com.otusfoss.mail.feature.folders.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockSpliceTest {
    private val block = "# BEGIN otus-rules\r\nif true {\r\n    stop;\r\n}\r\n# END otus-rules\r\n"
    private val newer = "# BEGIN otus-rules\r\n# newer\r\n# END otus-rules\r\n"
    private val require = "require [\"fileinto\"]; # otus-rules\r\n"
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
    fun theBlockGoesAtTheEndAndItsRequireAfterTheLeadingRequires() {
        listOf("\n", "\r\n").forEach { ending ->
            val script = handWritten.joinToString(ending, postfix = ending)
            val spliced = spliceBlock(script, block, require)
            val head = handWritten.take(3).joinToString(ending, postfix = ending)
            assertEquals(head + require + script.removePrefix(head) + block, spliced)
            assertEquals(script, spliced.replace(block, "").replace(require, ""), "Every other byte stays")
        }
    }

    @Test
    fun theScriptsOwnCommandsRunBeforeTheBlockStops() {
        val script = "require [\"copy\"];\nredirect :copy \"x@example.org\";\nvacation \"Away\";\n"
        val spliced = spliceBlock(script, block, require)
        assertTrue(spliced.indexOf("redirect") < spliced.indexOf("stop;"), spliced)
        assertTrue(spliced.indexOf("vacation") < spliced.indexOf(BLOCK_BEGIN), spliced)
    }

    @Test
    fun aScriptWithoutRequiresGetsTheRequireAtTheTop() {
        val script = "# Only a comment\nkeep;\n"
        assertEquals(require + script + block, spliceBlock(script, block, require))
        assertEquals(require + block, spliceBlock("", block, require))
        assertEquals(script + block, spliceBlock(script, block), "Rules that need nothing require nothing")
    }

    @Test
    fun aLastLineWithoutALineEndGetsOne() {
        assertEquals("require \"body\";\r\n$require$block", spliceBlock("require \"body\";", block, require))
        assertEquals(
            "require \"body\";\r\n${require}keep;\r\n$block",
            spliceBlock("require \"body\";keep;", block, require),
        )
    }

    @Test
    fun aBlockAtTheTopMovesToTheEndAndTheRequireInsideItGoes() {
        listOf("\n", "\r\n").forEach { ending ->
            val before = handWritten.take(3).joinToString(ending, postfix = ending)
            val after = handWritten.drop(3).joinToString(ending, postfix = ending)
            val old = "# BEGIN otus-rules\r\nrequire [\"fileinto\"];\r\nif true {\r\n}\r\n# END otus-rules\r\n"
            val oldBlock = old.replace("\r\n", ending)
            val spliced = spliceBlock(before + oldBlock + after, newer, require)
            assertEquals(before + require + after + newer, spliced)
            assertEquals(oldBlock, blockText(before + oldBlock + after))
        }
    }

    @Test
    fun savingAgainChangesNothingAndRulesThatNeedNothingDropTheRequire() {
        val script = handWritten.joinToString("\n", postfix = "\n")
        val once = spliceBlock(script, block, require)
        assertEquals(once, spliceBlock(once, block, require))
        assertEquals(script + newer, spliceBlock(once, newer))
    }

    @Test
    fun aBlockWithoutItsEndLineIsNoBlock() {
        assertNull(findBlock("# BEGIN otus-rules\nkeep;\n"))
        assertEquals(BlockSpan(0, block.length), findBlock(block))
        assertEquals(BlockSpan(0, block.length - 2), findBlock(block.removeSuffix("\r\n")))
    }
}
