package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card

/**
 * What a merge of [cards] keeps: the [name] of one of them, the [addresses] ticked, and the
 * [book] the merged card is saved in. [deleteOthers] removes the cards that are not saved over;
 * it is a separate choice and off unless the user turns it on.
 */
data class MergePlan(
    val cards: List<ContactCard>,
    val name: CardKey,
    val addresses: Set<String>,
    val book: BookKey,
    val deleteOthers: Boolean = false,
) {
    /** The card saved over: the first one already in [book], else none, and the merge creates a card. */
    val target: ContactCard? get() = cards.firstOrNull { book in it.books }

    /** The cards the merge removes when [deleteOthers] is on. */
    val removed: List<ContactCard> get() = if (deleteOthers) cards.filter { it != target } else emptyList()

    /**
     * The merged card. It starts from [target], or the card the name comes from, and takes the
     * ticked addresses, every number, and each property the base lacks from the others, so no
     * value is lost unless the user unticked it.
     */
    fun merged(): Card {
        val nameCard = cards.first { it.key == name }
        val base = target ?: nameCard
        val others = cards.filter { it != base }
        val emails =
            cards
                .flatMap { card ->
                    card.card.emails
                        .orEmpty()
                        .entries
                        .map { it.key to it.value }
                }.filter { (_, email) -> email.address.lowercase() in addresses.map(String::lowercase) }
                .distinctBy { (_, email) -> email.address.lowercase() }
                .mapIndexed { index, (_, email) -> "e${index + 1}" to email }
                .toMap()
        val phones =
            cards
                .flatMap {
                    it.card.phones
                        .orEmpty()
                        .values
                }.distinctBy { it.number.filter(Char::isDigit) }
                .mapIndexed { index, phone -> "p${index + 1}" to phone }
                .toMap()
        return others.fold(base.card) { card, other -> card.filledFrom(other.card) }.copy(
            name = nameCard.card.name,
            emails = emails.takeIf { it.isNotEmpty() },
            phones = phones.takeIf { it.isNotEmpty() },
            addressBookIds = mapOf(book.bookId to true),
            id = target?.key?.cardId,
        )
    }

    companion object {
        /**
         * A plan that keeps the first name a card has and every address, saved in the first card's
         * first writable book.
         */
        fun of(
            cards: List<ContactCard>,
            writable: List<ContactBook>,
        ): MergePlan? {
            val book =
                cards.firstNotNullOfOrNull { card -> writable.firstOrNull { it.key in card.books } }
                    ?: writable.firstOrNull()
                    ?: return null
            return MergePlan(
                cards,
                (cards.firstOrNull { it.hasName } ?: cards.first()).key,
                cards.flatMap { card -> card.emails.map { it.value } }.toSet(),
                book.key,
            )
        }
    }
}

/** This card with each property it lacks taken from [other]. */
private fun Card.filledFrom(other: Card): Card =
    copy(
        organizations = organizations ?: other.organizations,
        titles = titles ?: other.titles,
        anniversaries = anniversaries ?: other.anniversaries,
        notes = notes ?: other.notes,
        addresses = addresses ?: other.addresses,
        media = media ?: other.media,
        nicknames = nicknames ?: other.nicknames,
        links = links ?: other.links,
        onlineServices = onlineServices ?: other.onlineServices,
    )
