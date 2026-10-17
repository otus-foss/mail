package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.HeaderOperator
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleSupport
import com.otus.mail.feature.folders.domain.TextField
import com.otus.mail.feature.folders.domain.TextOperator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyRulesTest {
    private fun v1(
        rules: String,
        keep: String? = null,
    ) = "# Otus Mail rules.\r\n# otus-rules-v1 {\"rules\":[$rules]${keep?.let { ",\"keep\":\"$it\"" }.orEmpty()}}\r\n" +
        "require [\"fileinto\"];\r\n"

    private fun rule(
        folder: String,
        vararg conditions: Triple<String, String, String>,
        extra: String = "",
    ) = conditions.joinToString(
        ",",
        "{\"id\":\"x\",\"name\":\"Old\",\"folder\":\"$folder\",\"conditions\":[",
        "]$extra}",
    ) {
        "{\"field\":\"${it.first}\",\"operator\":\"${it.second}\",\"value\":\"${it.third}\"}"
    }

    private fun address(
        field: AddressField,
        match: AddressMatch,
        negated: Boolean = false,
    ) = RuleCondition.Address(field, match, negated)

    @Test
    fun addressComparisonsBecomeAddressesDomainsOrHeaders() {
        val read =
            readLegacyRules(
                v1(
                    listOf(
                        rule("A", Triple("from", "is", "jana@agency.example")),
                        rule("A", Triple("from", "endsWith", "@agency.example")),
                        rule("A", Triple("to", "startsWith", "billing@")),
                        rule("A", Triple("cc", "isNot", "team@labs.example")),
                        rule("A", Triple("from", "contains", "amazon")),
                        rule("A", Triple("from", "endsWith", "amazon.de")),
                    ).joinToString(","),
                ),
            )
        assertEquals(
            listOf(
                address(AddressField.From, AddressMatch.Exact("jana@agency.example")),
                address(AddressField.From, AddressMatch.Domain("agency.example")),
                address(AddressField.To, AddressMatch.LocalPart("billing")),
                address(AddressField.Cc, AddressMatch.Exact("team@labs.example"), negated = true),
                RuleCondition.Header("From", HeaderOperator.Contains, "amazon"),
                RuleCondition.Header("From", HeaderOperator.Contains, "amazon.de"),
            ).map(::FolderRule),
            read?.rules?.get("A"),
        )
    }

    @Test
    fun subjectAndBodyComparisonsBecomeText() {
        val read =
            readLegacyRules(
                v1(
                    listOf(
                        rule("S", Triple("subject", "contains", "Invoice")),
                        rule("S", Triple("subject", "startsWith", "[Acme]")),
                        rule("S", Triple("subject", "endsWith", "done?")),
                        rule("S", Triple("subject", "is", "Hello")),
                        rule("S", Triple("body", "doesNotContain", "Unsubscribe")),
                    ).joinToString(","),
                ),
            )
        assertEquals(
            listOf(
                RuleCondition.Text(TextField.Subject, TextOperator.Contains, "Invoice"),
                RuleCondition.Text(TextField.Subject, TextOperator.StartsWith, "[Acme]"),
                RuleCondition.Text(TextField.Subject, TextOperator.Matches, "*done\\?"),
                RuleCondition.Header("Subject", HeaderOperator.Is, "Hello"),
                RuleCondition.Text(TextField.Body, TextOperator.Contains, "Unsubscribe", negated = true),
            ).map(::FolderRule),
            read?.rules?.get("S"),
        )
    }

    @Test
    fun allOfRulesStayGroupsAndToOrCcSplitsThem() {
        val group = rule("G", Triple("recipient", "is", "me@home.example"), Triple("subject", "contains", "Report"))
        val anyOf =
            rule(
                "Y",
                Triple("from", "is", "a@x.example"),
                Triple("recipient", "is", "b@x.example"),
                extra = ",\"matchAll\":false",
            )
        val read = readLegacyRules(v1("$group,$anyOf"))
        val report = RuleCondition.Text(TextField.Subject, TextOperator.Contains, "Report")
        assertEquals(
            listOf(
                FolderRule(listOf(address(AddressField.To, AddressMatch.Exact("me@home.example")), report)),
                FolderRule(listOf(address(AddressField.Cc, AddressMatch.Exact("me@home.example")), report)),
            ),
            read?.rules?.get("G"),
        )
        assertEquals(
            listOf(
                address(AddressField.From, AddressMatch.Exact("a@x.example")),
                address(AddressField.To, AddressMatch.Exact("b@x.example")),
                address(AddressField.Cc, AddressMatch.Exact("b@x.example")),
            ).map(::FolderRule),
            read?.rules?.get("Y"),
        )
    }

    @Test
    fun aRuleThatWouldSplitIntoTooManyRulesIsDropped() {
        val recipients = { count: Int -> Array(count) { Triple("recipient", "is", "r$it@x.example") } }
        val read = readLegacyRules(v1("${rule("K", *recipients(6))},${rule("D", *recipients(30))}"))
        assertEquals(64, read?.rules?.get("K")?.size)
        assertNull(read?.rules?.get("D"))
    }

    @Test
    fun pausedRulesRegexAndAttachmentsAreDropped() {
        val read =
            readLegacyRules(
                v1(
                    listOf(
                        rule("P", Triple("from", "is", "a@x.example"), extra = ",\"enabled\":false"),
                        rule("R", Triple("from", "is", "a@x.example"), Triple("subject", "matchesRegex", "^x")),
                        rule("R", Triple("attachmentName", "endsWith", ".ics")),
                        rule("M", Triple("from", "is", "m@x.example"), extra = ",\"markRead\":true"),
                    ).joinToString(","),
                ),
            )
        assertEquals(
            mapOf("M" to listOf(FolderRule(address(AddressField.From, AddressMatch.Exact("m@x.example"))))),
            read?.rules,
        )
        assertNull(readLegacyRules("require \"fileinto\";\r\n"))
    }

    @Test
    fun anActiveOldScriptBecomesJustTheBlockOrMovesItIntoTheKeptScript() =
        runTest {
            val support = RuleSupport(setOf("fileinto", "mailboxid"))
            val old = SieveScriptObject("S1", RULES_SCRIPT, "B1", isActive = true)
            val kept = SieveScriptObject("S2", "roundcube", "B2")
            val texts =
                mapOf(
                    "S1" to v1(rule("A", Triple("from", "is", "a@x.example")), keep = "roundcube"),
                    "S2" to "keep;\n",
                )
            val plan = assertNotNull(planScript(listOf(old, kept), support) { texts.getValue(it.id) })
            assertEquals(kept, plan.target)
            assertEquals("keep;\n", plan.text)
            assertTrue(plan.migrated)
            assertEquals(listOf("A"), plan.rules.keys.toList())

            val alone =
                assertNotNull(planScript(listOf(old), support) { v1(rule("A", Triple("from", "is", "a@x.example"))) })
            assertEquals(old, alone.target)
            assertEquals("", alone.text, "The old script becomes just the block")
            assertTrue(alone.migrated)
        }

    @Test
    fun theBlockGoesIntoTheActiveScriptOrANewOneAndNeverIntoVacation() =
        runTest {
            val support = RuleSupport(setOf("fileinto"))
            val mine = SieveScriptObject("S3", "mine", "B3", isActive = true)
            val inactiveOld = SieveScriptObject("S1", RULES_SCRIPT, "B1")
            val plan = planScript(listOf(inactiveOld, mine), support) { "keep;\n" }
            assertEquals(ScriptPlan(mine, "keep;\n", emptyMap(), hasBlock = false), plan)
            assertEquals(ScriptPlan(null, "", emptyMap(), hasBlock = false), planScript(emptyList(), support) { "" })
            val vacation = SieveScriptObject("S4", "vacation", "B4", isActive = true)
            assertNull(planScript(listOf(vacation), support) { "" })
        }
}
