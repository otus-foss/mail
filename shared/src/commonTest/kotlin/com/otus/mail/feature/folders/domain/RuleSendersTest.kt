package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RuleSendersTest {
    private fun from(
        address: String,
        operator: RuleOperator = RuleOperator.Is,
    ) = RuleCondition(RuleField.From, operator, address)

    private fun account(vararg rules: MailRule) =
        AccountRules("a", "Work", RuleAvailability.Available(RuleSupport(emptySet())), rules.toList())

    @Test
    fun foldersListTheSendersWhoseMailRulesMoveThere() {
        val rules =
            account(
                MailRule("1", "Alice", listOf(from("alice@example.com")), folderId = "news"),
                MailRule("2", "Bob", listOf(from("bob@example.com")), folderId = "news"),
                MailRule("3", "Alice again", listOf(from("ALICE@example.com")), folderId = "news"),
                MailRule("4", "Carol", listOf(from("carol@example.com")), folderId = "work"),
            )
        assertEquals(
            mapOf("news" to listOf("alice@example.com", "bob@example.com"), "work" to listOf("carol@example.com")),
            rules.sendersByFolder(),
        )
    }

    @Test
    fun onlyRulesThatMoveAllOfASendersMailCount() {
        val subject = RuleCondition(RuleField.Subject, RuleOperator.Contains, "invoice")
        val rules =
            account(
                // Both conditions must match, so only the sender's invoices move.
                MailRule("1", "Invoices", listOf(from("dave@example.com"), subject), folderId = "f"),
                // Either condition is enough, so all of Erin's mail moves.
                MailRule("2", "Erin", listOf(from("erin@example.com"), subject), folderId = "f", matchAll = false),
                MailRule("3", "Domain", listOf(from("@example.com", RuleOperator.EndsWith)), folderId = "f"),
                MailRule("4", "Paused", listOf(from("finn@example.com")), folderId = "f", enabled = false),
            )
        assertEquals(mapOf("f" to listOf("erin@example.com")), rules.sendersByFolder())
    }
}
