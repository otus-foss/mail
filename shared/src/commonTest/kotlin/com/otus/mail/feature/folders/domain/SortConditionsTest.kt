package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SortConditionsTest {
    @Test
    fun aMessageOffersItsSenderDomainRecipientAndSubjectWord() {
        assertEquals(
            listOf(
                SortCondition(
                    SortBy.Sender,
                    "billing@acme.example",
                    RuleCondition.Address(AddressField.From, AddressMatch.Exact("billing@acme.example")),
                ),
                SortCondition(
                    SortBy.Domain,
                    "@acme.example",
                    RuleCondition.Address(AddressField.From, AddressMatch.Domain("acme.example")),
                ),
                SortCondition(
                    SortBy.Recipient,
                    "me@home.example",
                    RuleCondition.Address(AddressField.To, AddressMatch.Exact("me@home.example")),
                ),
                SortCondition(
                    SortBy.Subject,
                    "Invoice",
                    RuleCondition.Text(TextField.Subject, TextOperator.Contains, "Invoice"),
                ),
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
