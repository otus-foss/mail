package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card
import com.otus.mail.core.mail.EmailAddressKey

/** What an import does with a card whose address is already in the target book. */
enum class ImportMode(
    val label: String,
    val note: String,
) {
    Merge("Merge", "New details are added to the existing cards. Nothing is removed."),
    Skip("Skip", "Existing cards stay as they are."),
    AddAsNew("Add as new", "You get a second card for each of them."),
}

/**
 * An import of [cards] read from a file into [book]: which are new, and which match a card in
 * [book] by uid or address. [failures] are the file's cards that could not be read, by position.
 */
data class ImportPlan(
    val cards: List<Card>,
    val book: BookKey,
    val existing: List<ContactCard>,
    val failures: List<Pair<Int, String>> = emptyList(),
) {
    private val byAddress: Map<EmailAddressKey, ContactCard> =
        existing
            .filter { book in it.books }
            .flatMap { card -> card.emails.map { EmailAddressKey(it.value) to card } }
            .toMap()

    private val inBook: Map<String, ContactCard> =
        existing
            .filter { book in it.books }
            .mapNotNull { card -> card.card.uid?.let { it to card } }
            .toMap()

    // The uids the target account holds already, in any of its books.
    private val accountUids: Set<String> =
        existing
            .filter { it.key.loginId == book.loginId && it.key.accountId == book.accountId }
            .mapNotNull { it.card.uid }
            .toSet()

    /** The existing card [card] matches, if any: the one in [book] with its uid, else with one of its addresses. */
    fun match(card: Card): ContactCard? =
        card.uid?.let(inBook::get)
            ?: card.emails
                .orEmpty()
                .values
                .firstNotNullOfOrNull { byAddress[EmailAddressKey(it.address)] }

    val newCards: List<Card> get() = cards.filter { match(it) == null }
    val matched: List<Card> get() = cards.filter { match(it) != null }

    /** How many cards [mode] changes. */
    fun count(mode: ImportMode): Int = if (mode == ImportMode.Skip) newCards.size else cards.size

    /**
     * The changes [mode] makes, with [id] naming each. A new card keeps the file's uid unless the
     * account holds it already, in any book, or an earlier card of the file has it; then, or when it
     * has none, it gets one from [uid]. A server may allow a uid once per account, and refuses a
     * second card with it.
     */
    fun edits(
        mode: ImportMode,
        id: () -> String,
        uid: () -> String,
    ): List<ContactEdit> {
        val creates =
            cards
                .filter { mode == ImportMode.AddAsNew || match(it) == null }
                .withFreeUids(accountUids, uid)
                .map { ContactEdit.Create(id(), book, it.copy(id = null, addressBookIds = mapOf(book.bookId to true))) }
        // Every file card that matches one card merges into a single change, so none is lost.
        val merges =
            if (mode == ImportMode.Merge) {
                cards
                    .mapNotNull { card -> match(card)?.let { it to card } }
                    .groupBy({ it.first }, { it.second })
                    .map { (match, files) ->
                        ContactEdit.Update(id(), match.key, match.card, files.fold(match.card, Card::mergedWith))
                    }
            } else {
                emptyList()
            }
        return creates + merges
    }
}

/** These cards, each with a uid neither [taken] nor used by an earlier one; [uid] makes a new one. */
private fun List<Card>.withFreeUids(
    taken: Set<String>,
    uid: () -> String,
): List<Card> {
    val used = taken.toMutableSet()
    return map { card ->
        val own = card.uid
        val free = if (own != null && own !in used) own else uid()
        used += free
        card.copy(uid = free)
    }
}

/**
 * This card with what [file] adds: each property it lacks, and the addresses and numbers it does
 * not have yet, under keys it does not use. Its books and everything it has stay as they are.
 */
private fun Card.mergedWith(file: Card): Card {
    val newEmails =
        file.emails
            .orEmpty()
            .values
            .filter { email -> emails.orEmpty().values.none { it.address.equals(email.address, ignoreCase = true) } }
            .distinctBy { EmailAddressKey(it.address) }
    val newPhones =
        file.phones
            .orEmpty()
            .values
            .filter { phone -> phones.orEmpty().values.none { it.number.digits() == phone.number.digits() } }
            .distinctBy { it.number.digits() }
    val emailKeys = freeKeys("i", emails.orEmpty().keys).iterator()
    val phoneKeys = freeKeys("i", phones.orEmpty().keys).iterator()
    return filledFrom(file).copy(
        name = name ?: file.name,
        emails = (emails.orEmpty() + newEmails.associateBy { emailKeys.next() }).takeIf { it.isNotEmpty() },
        phones = (phones.orEmpty() + newPhones.associateBy { phoneKeys.next() }).takeIf { it.isNotEmpty() },
    )
}

private fun String.digits() = filter(Char::isDigit)
