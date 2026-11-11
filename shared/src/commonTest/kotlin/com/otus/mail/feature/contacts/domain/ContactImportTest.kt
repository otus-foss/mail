package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card
import com.otus.jscontact.EmailAddress
import com.otus.jscontact.Name
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactImportTest {
    private val book = BookKey("ada", "a", "home")
    private val work = BookKey("ada", "a", "work")
    private val existing =
        ContactCard(
            CardKey("ada", "a", "c1"),
            Card(
                emails = mapOf("e1" to EmailAddress("grace@example.org"), "i1" to EmailAddress("g@navy.example")),
                addressBookIds = mapOf("home" to true, "work" to true),
            ),
            listOf(book, work),
        )

    @Test
    fun aMergeKeepsEveryAddressAndBookTheCardHad() {
        val file =
            Card(
                name = Name(full = "Grace"),
                emails =
                    mapOf(
                        "x" to EmailAddress("grace@example.org"),
                        "y" to EmailAddress("new@example.org"),
                    ),
            )
        val merged = merged(listOf(file))
        assertEquals(
            setOf("grace@example.org", "g@navy.example", "new@example.org"),
            merged.emails
                .orEmpty()
                .values
                .map { it.address }
                .toSet(),
        )
        assertEquals(mapOf("home" to true, "work" to true), merged.addressBookIds)
    }

    @Test
    fun fileCardsMatchingOneCardMergeIntoOneChange() {
        val first =
            Card(emails = mapOf("x" to EmailAddress("grace@example.org"), "y" to EmailAddress("one@example.org")))
        val second = Card(emails = mapOf("x" to EmailAddress("g@navy.example"), "y" to EmailAddress("two@example.org")))
        val plan = ImportPlan(listOf(first, second), book, listOf(existing))
        val edits = plan.edits(ImportMode.Merge, id = { "id" }, uid = { "fresh" })
        assertEquals(1, edits.size)
        val after = (edits.single() as ContactEdit.Update).after
        assertEquals(4, after.emails.orEmpty().size)
    }

    @Test
    fun aUidTheAccountHoldsInAnotherBookIsReplaced() {
        val trusted = BookKey("ada", "a", "trusted")
        val imported = ContactCard(CardKey("ada", "a", "c2"), Card(uid = "urn:uuid:1"), listOf(trusted))
        val file = Card(name = Name(full = "Grace"), uid = "urn:uuid:1")
        val plan = ImportPlan(listOf(file), book, listOf(existing, imported))
        assertEquals(listOf("urn:uuid:new"), plan.createdUids(ImportMode.Merge))
    }

    @Test
    fun aFreeUidIsKeptAndAMissingOrRepeatedOneIsMade() {
        val elsewhere =
            ContactCard(CardKey("ada", "other", "c2"), Card(uid = "urn:uuid:1"), listOf(BookKey("ada", "other", "x")))
        val files = listOf(Card(uid = "urn:uuid:1"), Card(uid = "urn:uuid:1"), Card())
        val plan = ImportPlan(files, book, listOf(existing, elsewhere))
        assertEquals(listOf("urn:uuid:1", "urn:uuid:new", "urn:uuid:new"), plan.createdUids(ImportMode.AddAsNew))
    }

    @Test
    fun aCardWithTheUidOfOneInTheBookMatchesIt() {
        val same = existing.copy(card = existing.card.copy(uid = "urn:uuid:1"))
        val plan = ImportPlan(listOf(Card(name = Name(full = "Grace"), uid = "urn:uuid:1")), book, listOf(same))
        assertEquals(same, plan.match(plan.cards.single()))
        assertEquals(0, plan.count(ImportMode.Skip))
        assertEquals(listOf("urn:uuid:new"), plan.createdUids(ImportMode.AddAsNew))
    }

    private fun ImportPlan.createdUids(mode: ImportMode): List<String?> =
        edits(mode, id = { "id" }, uid = { "urn:uuid:new" })
            .filterIsInstance<ContactEdit.Create>()
            .map { it.card.uid }

    private fun merged(files: List<Card>): Card {
        val plan = ImportPlan(files, book, listOf(existing))
        return (plan.edits(ImportMode.Merge, id = { "id" }, uid = { "fresh" }).single() as ContactEdit.Update).after
    }
}
