package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SortConditionsTest {
    @Test
    fun aMessageOffersItsSenderDomainRecipientAndSubjectWord() {
        assertEquals(
            listOf(
                SortCondition(SortBy.Sender, RuleCondition(RuleField.From, RuleOperator.Is, "billing@acme.example")),
                SortCondition(SortBy.Domain, RuleCondition(RuleField.From, RuleOperator.EndsWith, "@acme.example")),
                SortCondition(SortBy.Recipient, RuleCondition(RuleField.Recipient, RuleOperator.Is, "me@home.example")),
                SortCondition(SortBy.Subject, RuleCondition(RuleField.Subject, RuleOperator.Contains, "Invoice")),
            ),
            sortConditions("billing@acme.example", "me@home.example", "Your Invoice #1042"),
        )
    }

    @Test
    fun partsTheMessageLacksAreLeftOut() {
        assertEquals(
            listOf(SortBy.Sender),
            sortConditions("postmaster", recipient = " ", subject = "Hi").map { it.kind },
        )
        assertEquals(emptyList(), sortConditions("", "", ""))
    }

    @Test
    fun theKeywordIsTheFirstLongUncommonWordWithoutPunctuation() {
        assertEquals("order", subjectKeyword("Re: Fwd: your order has shipped!"))
        assertEquals("Rechnung", subjectKeyword("„Rechnung“ für Oktober"))
        assertNull(subjectKeyword("Re: this is just for you"))
        assertNull(subjectKeyword("A1B2 2026"))
    }
}
