package com.otusfoss.mail.feature.contacts.presentation

import com.otusfoss.core.jscontact.Card
import com.otusfoss.core.jscontact.Name
import com.otusfoss.mail.feature.contacts.domain.BookKey
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactCard
import com.otusfoss.mail.feature.contacts.domain.ContactRepository
import com.otusfoss.mail.feature.contacts.domain.ContactsSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsCachedFirstTest {
    @Test
    fun theCachedCardsShowWhileTheServerAnswers() =
        runTest {
            val repository = SlowRepository()
            val state = ContactsState(repository, ContactSync(repository, null, this), this)
            state.loadCards()
            runCurrent()
            assertEquals(listOf("Ada"), state.cards.map { it.displayName })
            repository.answer.complete(snapshot("Ada Lovelace"))
            runCurrent()
            assertEquals(listOf("Ada Lovelace"), state.cards.map { it.displayName })
        }

    private class SlowRepository : ContactRepository {
        val answer = CompletableDeferred<ContactsSnapshot>()

        override suspend fun load() = answer.await()

        override suspend fun cached() = snapshot("Ada")

        override suspend fun create(
            book: BookKey,
            card: Card,
        ) = CardKey(book.loginId, book.accountId, "server")

        override suspend fun update(
            key: CardKey,
            before: Card,
            after: Card,
        ) = Unit

        override suspend fun delete(keys: List<CardKey>) = Unit
    }

    private companion object {
        val book = BookKey("ada", "a", "b")

        fun snapshot(name: String) =
            ContactsSnapshot(
                books = emptyList(),
                cards = listOf(ContactCard(CardKey("ada", "a", "c1"), Card(name = Name(full = name)), listOf(book))),
                hasContacts = true,
            )
    }
}
