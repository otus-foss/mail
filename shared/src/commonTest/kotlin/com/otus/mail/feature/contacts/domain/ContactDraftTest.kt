package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card
import com.otus.jscontact.EmailAddress
import com.otus.jscontact.OrgUnit
import com.otus.jscontact.Organization
import com.otus.jscontact.Title
import com.otus.jscontact.TitleKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactDraftTest {
    private val key = CardKey("ada", "a", "c1")

    @Test
    fun aNewAddressNeverReplacesOneTheCardKeeps() {
        val card = Card(emails = mapOf("e1n" to EmailAddress("a@example.org"), "e2n" to EmailAddress("b@example.org")))
        val draft =
            ContactDraft(emails = listOf(DraftValue("e2n", "b@example.org"), DraftValue(value = "c@example.org")))
        val saved =
            draft
                .applyTo(card)
                .emails
                .orEmpty()
                .values
                .map { it.address }
        assertEquals(listOf("b@example.org", "c@example.org"), saved)
    }

    @Test
    fun savingKeepsWhatTheEditorDoesNotShow() {
        val card =
            Card(
                organizations = mapOf("o" to Organization("Analytical Engines", units = listOf(OrgUnit("Research")))),
                titles = mapOf("t" to Title("Lead", kind = TitleKind.Role)),
            )
        val saved = ContactDraft.of(ContactCard(key, card, emptyList())).copy(title = "Head").applyTo(card)
        assertEquals(listOf(OrgUnit("Research")), saved.organizations?.get("o")?.units)
        assertEquals(Title("Head", kind = TitleKind.Role), saved.titles?.get("t"))
    }
}
