package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RuleSendersTest {
    private fun from(address: String) = RuleCondition.Address(AddressField.From, AddressMatch.Exact(address))

    private fun account(rules: Map<String, List<FolderRule>>) =
        AccountFolderRules("a", "Work", RuleAvailability.Available(RuleSupport(emptySet())), rules)

    @Test
    fun foldersListTheSendersWhoseMailRulesMoveThere() {
        val rules =
            account(
                mapOf(
                    "news" to
                        listOf(
                            FolderRule(from("alice@example.com")),
                            FolderRule(from("bob@example.com")),
                            FolderRule(from("ALICE@example.com")),
                        ),
                    "work" to listOf(FolderRule(from("carol@example.com"))),
                ),
            )
        assertEquals(
            mapOf("news" to listOf("alice@example.com", "bob@example.com"), "work" to listOf("carol@example.com")),
            rules.sendersByFolder(),
        )
    }

    @Test
    fun onlyRulesThatMoveAllOfASendersMailCount() {
        val subject = RuleCondition.Text(TextField.Subject, TextOperator.Contains, "invoice")
        val rules =
            account(
                mapOf(
                    "f" to
                        listOf(
                            // Both conditions must match, so only the sender's invoices move.
                            FolderRule(listOf(from("dave@example.com"), subject)),
                            FolderRule(from("erin@example.com")),
                            FolderRule(RuleCondition.Address(AddressField.From, AddressMatch.Domain("example.com"))),
                            FolderRule(from("finn@example.com").copy(negated = true)),
                        ),
                ),
            )
        assertEquals(mapOf("f" to listOf("erin@example.com")), rules.sendersByFolder())
    }
}
