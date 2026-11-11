package com.otusfoss.mail.feature.folders.presentation

import com.otusfoss.mail.feature.folders.domain.AddressField
import com.otusfoss.mail.feature.folders.domain.AddressMatch
import com.otusfoss.mail.feature.folders.domain.FolderRule
import com.otusfoss.mail.feature.folders.domain.RuleCondition
import com.otusfoss.mail.feature.folders.domain.TextField
import com.otusfoss.mail.feature.folders.domain.TextOperator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuleSuggestionsTest {
    private val known =
        List(9) { KnownAddress(AddressField.From, "jana@website-agency.example", "Jana Meier") } +
            List(5) { KnownAddress(AddressField.From, "billing@website-agency.example") } +
            KnownAddress(AddressField.To, "projects@rubylabs.design")

    @Test
    fun theDomainLeadsThenAddressesByHowMuchMailTheySent() {
        val found = ruleSuggestions(RuleKind.From, "website-ag", known)
        assertEquals(
            listOf(
                "Anyone at @website-agency.example" to "14 messages",
                "jana@website-agency.example" to "Jana Meier · 9 messages",
                "billing@website-agency.example" to "5 messages",
            ),
            found.map { it.title to it.subtitle },
        )
    }

    @Test
    fun aTypedAddressIsOfferedWhenNoMailHasIt() {
        val typed = ruleSuggestions(RuleKind.To, "team@rubylabs.design", known).first()
        assertEquals(
            RuleCondition.Address(AddressField.To, AddressMatch.Exact("team@rubylabs.design")),
            typed.condition,
        )
        val local = ruleSuggestions(RuleKind.From, "billing@", emptyList()).single()
        assertEquals("billing@ any domain", local.title)
        assertEquals(emptyList(), ruleSuggestions(RuleKind.Subject, "jana", known))
    }

    @Test
    fun subjectAndListRulesTakeTheTypedText() {
        assertEquals(
            RuleCondition.Text(TextField.Subject, TextOperator.Contains, "Website"),
            typedCondition(RuleKind.Subject, " Website "),
        )
        assertEquals(
            RuleCondition.MailingList("releases.github.example"),
            typedCondition(RuleKind.MailingList, "<releases.github.example>"),
        )
        assertNull(typedCondition(RuleKind.Subject, "  "))
    }

    @Test
    fun theSheetEditsOnlyRulesItCanShowWhole() {
        val domain = RuleCondition.Address(AddressField.From, AddressMatch.Domain("website-agency.example"))
        assertEquals(
            RuleSheetDraft(RuleKind.From, "@website-agency.example", domain, editing = 2),
            FolderRule(domain).sheetDraft(2),
        )
        val startsWith = RuleCondition.Text(TextField.Subject, TextOperator.StartsWith, "[Acme]")
        assertNull(FolderRule(startsWith).sheetDraft(0))
        assertNull(FolderRule(domain.copy(negated = true)).sheetDraft(0))
        assertNull(FolderRule(listOf(domain, startsWith)).sheetDraft(0))
        assertNull(FolderRule(domain.copy(field = AddressField.Cc)).sheetDraft(0))
    }
}
