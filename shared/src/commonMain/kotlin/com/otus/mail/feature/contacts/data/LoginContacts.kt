package com.otus.mail.feature.contacts.data

import com.otus.jmap.contacts.AddressBookOrder
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard

/** One login's books and cards; [hasContacts] is false when its server offers no contacts. */
internal class LoginContacts(
    val books: List<ContactBook>,
    val cards: List<ContactCard>,
    val hasContacts: Boolean,
)

/**
 * What [login] shows of [accounts]: the books the user may read, in [AddressBookOrder], and the
 * cards in at least one of them.
 */
internal fun loginContacts(
    login: JmapLogin,
    accounts: List<AccountContacts>,
): LoginContacts {
    val parts = accounts.map { login.contactsOf(it) }
    return LoginContacts(parts.flatMap { it.first }, parts.flatMap { it.second }, hasContacts = true)
}

private fun JmapLogin.contactsOf(contacts: AccountContacts): Pair<List<ContactBook>, List<ContactCard>> {
    val account = contacts.account
    val books =
        contacts.books
            .filter { it.myRights.mayRead }
            .sortedWith(AddressBookOrder)
            .map { book ->
                ContactBook(
                    BookKey(id, account.id, book.id),
                    bookName(book.name, this, account.name),
                    name,
                    isDefault = book.isDefault && !account.isShared,
                    canWrite = book.myRights.mayWrite && !account.isReadOnly,
                    sharedBy = account.name.takeIf { account.isShared },
                )
            }
    val known = books.map { it.key.bookId }.toSet()
    val cards =
        contacts.cards.map { card ->
            ContactCard(
                CardKey(id, account.id, requireNotNull(card.id)),
                card,
                card.addressBookIds
                    .orEmpty()
                    .filter { (bookId, on) -> on && bookId in known }
                    .keys
                    .map { BookKey(id, account.id, it) },
            )
        }
    return books to cards.filter { it.books.isNotEmpty() }
}
