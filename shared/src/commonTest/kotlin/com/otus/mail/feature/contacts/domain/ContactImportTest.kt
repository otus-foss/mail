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
                emails = mapOf("e1" to EmailAddress("grace@example.org"), "i1" to EmailAddress("g@navy.mil")),
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
            setOf("grace@example.org", "g@navy.mil", "new@example.org"),
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
        val second = Card(emails = mapOf("x" to EmailAddress("g@navy.mil"), "y" to EmailAddress("two@example.org")))
        val plan = ImportPlan(listOf(first, second), book, listOf(existing))
        val edits = plan.edits(ImportMode.Merge) { "id" }
        assertEquals(1, edits.size)
        val after = (edits.single() as ContactEdit.Update).after
        assertEquals(4, after.emails.orEmpty().size)
    }

    private fun merged(files: List<Card>): Card {
        val plan = ImportPlan(files, book, listOf(existing))
        return (plan.edits(ImportMode.Merge) { "id" }.single() as ContactEdit.Update).after
    }
}
