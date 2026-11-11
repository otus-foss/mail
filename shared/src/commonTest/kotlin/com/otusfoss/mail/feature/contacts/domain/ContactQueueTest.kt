package com.otusfoss.mail.feature.contacts.domain

import com.otusfoss.core.jscontact.Card
import com.otusfoss.core.jscontact.Name
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactQueueTest {
    private val book = BookKey("ada", "a", "b")

    @Test
    fun aCardWhoseDeleteWaitsForARefusedMergeStillShows() {
        val card = ContactCard(CardKey("ada", "a", "c1"), Card(name = Name(full = "Ada")), listOf(book))
        val merge = ContactEdit.Create("m", book, Card(name = Name(full = "Ada Lovelace")))
        val edits = listOf(merge, ContactEdit.Delete("d", card.key, waitsFor = merge.id))
        val shown = applyEdits(listOf(card), edits, mapOf(merge.id to ContactWriteFailure.ReadOnly))
        assertEquals(listOf(card.key, merge.key), shown.map { it.key })
    }
}
