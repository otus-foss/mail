package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card

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
 * [book] by address. [failures] are the file's cards that could not be read, by position.
 */
data class ImportPlan(
    val cards: List<Card>,
    val book: BookKey,
    val existing: List<ContactCard>,
    val failures: List<Pair<Int, String>> = emptyList(),
) {
    private val byAddress: Map<String, ContactCard> =
        existing
            .filter { book in it.books }
            .flatMap { card -> card.emails.map { it.value.lowercase() to card } }
            .toMap()

    /** The existing card [card] matches, if any. */
    fun match(card: Card): ContactCard? =
        card.emails
            .orEmpty()
            .values
            .firstNotNullOfOrNull { byAddress[it.address.lowercase()] }

    val newCards: List<Card> get() = cards.filter { match(it) == null }
    val matched: List<Card> get() = cards.filter { match(it) != null }

    /** How many cards [mode] changes. */
    fun count(mode: ImportMode): Int = if (mode == ImportMode.Skip) newCards.size else cards.size

    /** The changes [mode] makes, with [id] naming each. */
    fun edits(
        mode: ImportMode,
        id: () -> String,
    ): List<ContactEdit> =
        cards.mapNotNull { card ->
            val match = match(card)
            when {
                match == null || mode == ImportMode.AddAsNew -> {
                    ContactEdit.Create(id(), book, card.copy(id = null, addressBookIds = mapOf(book.bookId to true)))
                }

                mode == ImportMode.Skip -> {
                    null
                }

                else -> {
                    val plan =
                        MergePlan(
                            listOf(match, ContactCard(match.key, card, listOf(book))),
                            match.key,
                            emptySet(),
                            book,
                        )
                    val merged =
                        plan.merged().copy(
                            emails = (match.card.emails.orEmpty() + card.renumbered(match.card)),
                        )
                    ContactEdit.Update(id(), match.key, match.card, merged)
                }
            }
        }
}

/** [card]'s addresses that [existing] lacks, under keys [existing] does not use. */
private fun Card.renumbered(existing: Card) =
    emails
        .orEmpty()
        .values
        .filter { email ->
            existing.emails
                .orEmpty()
                .values
                .none { it.address.equals(email.address, ignoreCase = true) }
        }.mapIndexed { index, email -> "i${index + 1}" to email }
        .toMap()
