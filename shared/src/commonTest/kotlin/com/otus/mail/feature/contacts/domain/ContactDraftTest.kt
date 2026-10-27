package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card
import com.otus.jscontact.EmailAddress
import com.otus.jscontact.Name
import com.otus.jscontact.NameComponent
import com.otus.jscontact.NameComponentKind
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

    @Test
    fun addingAnAddressKeepsTheNameAsTheCardHasIt() {
        val name =
            Name(
                components =
                    listOf(
                        NameComponent("Dr.", NameComponentKind.Title),
                        NameComponent("Ada", NameComponentKind.Given),
                        NameComponent("King", NameComponentKind.Surname),
                    ),
                isOrdered = true,
                defaultSeparator = " ",
                full = "Dr. Ada King, Countess of Lovelace",
            )
        assertEquals(name, withNewAddress(Card(name = name)).name)
    }

    @Test
    fun addingAnAddressKeepsANameWithOnlyItsFullForm() {
        val name = Name(full = "Ada Lovelace")
        assertEquals(name, withNewAddress(Card(name = name)).name)
    }

    @Test
    fun editingTheNameRebuildsIt() {
        val card = Card(name = Name(full = "Ada Lovelace"))
        val draft = ContactDraft.of(ContactCard(key, card, emptyList()))
        val saved = draft.copy(given = "Ada", surname = "King").applyTo(card)
        assertEquals("Ada King", saved.name?.full)
    }

    private fun withNewAddress(card: Card): Card {
        val draft = ContactDraft.of(ContactCard(key, card, emptyList()))
        return draft.copy(emails = draft.emails + DraftValue(value = "ada@example.org")).applyTo(card)
    }
}
