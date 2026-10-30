package com.otus.mail.feature.contacts.domain

/**
 * One person as the list shows them: usually one card, or several the user chose to show as one
 * with [ContactLinks]. The cards stay separate on the server. The first card speaks for the person.
 */
data class ContactPerson(
    val cards: List<ContactCard>,
) {
    val main: ContactCard get() = cards.first()
    val name: String get() = main.displayName

    /** Every address once, the main card's first. */
    val emails: List<ContactValue>
        get() = cards.flatMap { it.emails }.distinctBy { it.value.lowercase() }

    /** The person's name with their preferred address, to write to; null without an address. */
    val preferredAddress: ContactAddress? get() = emails.firstOrNull()?.let { ContactAddress(name, it.value) }

    /** The letter the list files the person under, or `#`. */
    val letter: String
        get() = name.firstOrNull()?.takeIf { it.isLetter() }?.uppercase() ?: "#"

    fun matches(query: String): Boolean =
        query
            .trim()
            .lowercase()
            .split(' ')
            .filter { it.isNotEmpty() }
            .all { word ->
                cards.any { card -> word in card.displayName.lowercase() } ||
                    emails.any { word in it.value.lowercase() }
            }
}

/**
 * Cards the user chose to show as one person, each set by its card keys. A set whose cards are
 * gone keeps the rest together.
 */
data class ContactLinks(
    val sets: List<Set<CardKey>> = emptyList(),
) {
    fun linked(
        a: CardKey,
        b: CardKey,
    ): Boolean = sets.any { a in it && b in it }

    /** Joins the sets that hold any of [keys] with [keys] into one. */
    fun link(keys: Set<CardKey>): ContactLinks {
        val (touched, rest) = sets.partition { set -> set.any { it in keys } }
        return ContactLinks(rest + setOf(touched.flatten().toSet() + keys))
    }

    /** Takes [key] out of its set, as when its card is deleted or merged away. */
    fun without(key: CardKey): ContactLinks = ContactLinks(sets.map { it - key }.filter { it.size > 1 })
}

/** [cards] that are not groups, joined by [links], in name order. */
fun people(
    cards: List<ContactCard>,
    links: ContactLinks,
): List<ContactPerson> {
    val individuals = cards.filterNot { it.isGroup }
    val byKey = individuals.associateBy { it.key }
    val linked = links.sets.map { set -> set.mapNotNull(byKey::get) }.filter { it.isNotEmpty() }
    val inSets = linked.flatten().map { it.key }.toSet()
    return (linked + individuals.filterNot { it.key in inSets }.map(::listOf))
        .map(::ContactPerson)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

/** Two cards that may be the same person, because they share [address]. */
data class PossibleDuplicate(
    val first: ContactCard,
    val second: ContactCard,
    val address: String,
)

/**
 * Pairs of cards that share an address and are not shown as one already. Addresses compare
 * without case only; plus tags and dots are left alone, since providers treat them differently.
 * [dismissed] pairs the user said are different people stay out.
 */
fun duplicates(
    cards: List<ContactCard>,
    links: ContactLinks,
    dismissed: Set<Set<CardKey>> = emptySet(),
): List<PossibleDuplicate> =
    cards
        .filterNot { it.isGroup }
        .flatMap { card -> card.emails.map { it.value.lowercase() to card } }
        .groupBy({ it.first }, { it.second })
        .flatMap { (address, sharing) ->
            sharing
                .distinctBy { it.key }
                .zipWithNext()
                .map { (a, b) -> PossibleDuplicate(a, b, address) }
        }.filterNot { links.linked(it.first.key, it.second.key) || setOf(it.first.key, it.second.key) in dismissed }
        .distinctBy { setOf(it.first.key, it.second.key) }
