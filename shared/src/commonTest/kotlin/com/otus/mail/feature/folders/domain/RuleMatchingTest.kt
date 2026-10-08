package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleMatchingTest {
    private val receipt =
        RuleMessage(
            from = listOf(RuleAddress("Amazon", "auto-confirm@amazon.com")),
            to = listOf(RuleAddress(null, "ada@example.org")),
            cc = listOf(RuleAddress("Team", "team@example.org")),
            subject = "Your order",
            attachmentTypes = listOf("text/calendar"),
            attachmentNames = listOf("invite.ics"),
        )

    private fun rule(
        vararg conditions: RuleCondition,
        matchAll: Boolean = true,
    ) = MailRule("r", "", conditions.toList(), "f", matchAll)

    private fun condition(
        field: RuleField,
        operator: RuleOperator,
        value: String,
    ) = RuleCondition(field, operator, value)

    @Test
    fun addressesCompareExactlyAndHeadersIncludeNames() {
        assertTrue(rule(condition(RuleField.From, RuleOperator.Contains, "amazon")).matches(receipt))
        assertTrue(rule(condition(RuleField.From, RuleOperator.Is, "AUTO-confirm@amazon.com")).matches(receipt))
        assertFalse(
            rule(condition(RuleField.From, RuleOperator.Is, "Amazon")).matches(receipt),
            "Is compares the address",
        )
        assertTrue(rule(condition(RuleField.From, RuleOperator.EndsWith, "@amazon.com")).matches(receipt))
        assertTrue(rule(condition(RuleField.Recipient, RuleOperator.Is, "team@example.org")).matches(receipt))
        assertTrue(rule(condition(RuleField.Cc, RuleOperator.MatchesRegex, "^team <")).matches(receipt))
    }

    @Test
    fun negationsAndMatchModesCombine() {
        val notFromBob = condition(RuleField.From, RuleOperator.IsNot, "bob@example.org")
        val ics = condition(RuleField.AttachmentName, RuleOperator.EndsWith, ".ICS")
        val pdf = condition(RuleField.AttachmentType, RuleOperator.Is, "application/pdf")
        assertTrue(rule(notFromBob, ics).matches(receipt))
        assertFalse(rule(ics, pdf).matches(receipt))
        assertTrue(rule(ics, pdf, matchAll = false).matches(receipt))
        assertTrue(rule(condition(RuleField.Subject, RuleOperator.DoesNotContain, "invoice")).matches(receipt))
        assertTrue(
            rule(condition(RuleField.Cc, RuleOperator.DoesNotContain, "x")).matches(RuleMessage()),
            "A missing header contains nothing, as on the server",
        )
    }

    @Test
    fun bodyRulesOnlyRunOnTheServer() {
        val body = rule(condition(RuleField.Body, RuleOperator.DoesNotContain, "unsubscribe"))
        assertTrue(body.checksBody)
        assertFalse(body.matches(receipt))
    }

    @Test
    fun problemsAreFoundBeforeSaving() {
        val all = RuleSupport(setOf("fileinto", "mailboxid", "regex", "imap4flags", "body", "mime"))
        val basic = RuleSupport(setOf("fileinto", "mailboxid"))
        val regex = rule(condition(RuleField.Subject, RuleOperator.MatchesRegex, "[unclosed"))
        assertEquals(RuleProblem.NoConditions, ruleProblem(rule(), all, folderExists = true))
        assertEquals(
            RuleProblem.EmptyValue,
            ruleProblem(rule(condition(RuleField.To, RuleOperator.Is, " ")), all, true),
        )
        assertEquals(RuleProblem.InvalidRegex, ruleProblem(regex, all, true))
        val body = rule(condition(RuleField.Body, RuleOperator.Contains, "x"))
        assertEquals(RuleProblem.Unsupported, ruleProblem(body, basic, true))
        assertEquals(RuleProblem.NoFolder, ruleProblem(body, all, folderExists = false))
        assertNull(ruleProblem(body, all, folderExists = true))
    }
}
