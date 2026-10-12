package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import com.otus.mail.feature.folders.domain.RuleSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Scripts read back from a server may hold rules this app would never write. */
class RuleScriptValidationTest {
    private val support = RuleSupport(setOf("fileinto", "mailboxid", "include"))
    private val receipts =
        MailRule("r1", "Receipts", listOf(RuleCondition(RuleField.From, RuleOperator.Contains, "amazon")), "M7")

    @Test
    fun aRuleWithoutConditionsIsLeftOutSoTheNextSaveIsValidSieve() {
        val stored = encodeRules(RuleScript(listOf(receipts, receipts.copy(id = "r2", conditions = emptyList()))))
        assertTrue("allof ()" in stored, "The broken rule is in the stored script")

        val read = decodeRules(stored, support)

        assertEquals(ReadRules(RuleScript(listOf(receipts)), changedElsewhere = true), read)
        assertFalse("allof ()" in encodeRules(read.script))
    }

    @Test
    fun theRulesScriptAVacationReplyOrAnUnsupportedIncludeIsNeverKept() {
        listOf(RULES_SCRIPT, "vacation", "Vacation", "", "other\u0000script").forEach { keep ->
            val read = decodeRules(encodeRules(RuleScript(listOf(receipts), keep)), support)
            assertEquals(ReadRules(RuleScript(listOf(receipts)), changedElsewhere = true), read, keep)
        }
        val withoutInclude = RuleSupport(setOf("fileinto", "mailboxid"))
        val read = decodeRules(encodeRules(RuleScript(listOf(receipts), "roundcube")), withoutInclude)
        assertEquals(ReadRules(RuleScript(listOf(receipts)), changedElsewhere = true), read)
    }

    @Test
    fun controlCharactersNeverReachTheSieveAndRulesHoldingThemAreLeftOut() {
        val hostile =
            receipts.copy(
                id = "r2",
                name = "Bad\u0000name",
                conditions = listOf(RuleCondition(RuleField.Subject, RuleOperator.Is, "a\u0000\u0007b")),
                folderId = "M\u00008",
            )
        val stored = encodeRules(RuleScript(listOf(receipts, hostile)))
        val sieve = stored.lines().drop(2).joinToString("\n")
        assertFalse(sieve.any { it < ' ' && it !in "\t\r\n" }, sieve)
        assertTrue("# Badname" in sieve && "\"ab\"" in sieve && "\"M8\"" in sieve, sieve)

        assertEquals(ReadRules(RuleScript(listOf(receipts)), changedElsewhere = true), decodeRules(stored, support))
    }

    @Test
    fun aLoneCarriageReturnBecomesASpaceAndCrLineEndsReadBackUnchanged() {
        val split =
            receipts.copy(
                name = "Line\rbreak",
                conditions = listOf(RuleCondition(RuleField.Subject, RuleOperator.Contains, "x\ry")),
            )
        val stored = encodeRules(RuleScript(listOf(split)))
        assertTrue("# Line break\r\n" in stored && "\"x y\"" in stored, stored)
        assertFalse(Regex("\r(?!\n)").containsMatchIn(stored), "No lone CR")

        val crOnly = stored.replace("\r\n", "\r")
        assertEquals(ReadRules(RuleScript(listOf(split)), changedElsewhere = false), decodeRules(crOnly, support))
    }
}
