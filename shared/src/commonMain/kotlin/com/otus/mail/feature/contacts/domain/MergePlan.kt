package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card
import com.otus.jscontact.Relation
import com.otus.mail.core.mail.EmailAddressKey

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
     * ticked addresses, every number, and every other value of the others it lacks, so no value is
     * lost unless the user unticked it.
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
                }.filter { (_, email) -> EmailAddressKey(email.address) in addresses.map { EmailAddressKey(it) } }
                .distinctBy { (_, email) -> EmailAddressKey(email.address) }
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
        val merged =
            others.fold(base.card) { card, other -> card.unitedWith(other.card) }.copy(
                name = nameCard.card.name,
                emails = emails.takeIf { it.isNotEmpty() },
                phones = phones.takeIf { it.isNotEmpty() },
                addressBookIds = target?.card?.addressBookIds.orEmpty() + (book.bookId to true),
                id = target?.key?.cardId,
            )
        return merged.copy(localizations = localizationsFor(merged, (listOf(base) + others).map { it.card }))
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
internal fun Card.filledFrom(other: Card): Card =
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

/**
 * This card with every value of [other] it lacks. Its own values and their keys stay; a value of
 * [other] whose key it already uses gets a free key. Sets and the relations keyed by uid join by
 * key instead, since their keys are the values. Single values come from [other] only where this
 * card has none, and the merge sets the name, addresses, numbers, books and localizations itself.
 */
private fun Card.unitedWith(other: Card): Card =
    copy(
        relatedTo = relatedTo.joinedWith(other.relatedTo) { own, theirs -> own.joinedWith(theirs) },
        members = members.joinedWith(other.members),
        nicknames = nicknames.unitedWith(other.nicknames),
        organizations = organizations.unitedWith(other.organizations),
        speakToAs = speakToAs ?: other.speakToAs,
        titles = titles.unitedWith(other.titles),
        onlineServices = onlineServices.unitedWith(other.onlineServices),
        preferredLanguages = preferredLanguages.unitedWith(other.preferredLanguages),
        calendars = calendars.unitedWith(other.calendars),
        schedulingAddresses = schedulingAddresses.unitedWith(other.schedulingAddresses),
        addresses = addresses.unitedWith(other.addresses),
        cryptoKeys = cryptoKeys.unitedWith(other.cryptoKeys),
        directories = directories.unitedWith(other.directories),
        links = links.unitedWith(other.links),
        media = media.unitedWith(other.media),
        anniversaries = anniversaries.unitedWith(other.anniversaries),
        keywords = keywords.joinedWith(other.keywords),
        notes = notes.unitedWith(other.notes),
        personalInfo = personalInfo.unitedWith(other.personalInfo),
        language = language ?: other.language,
        extra = other.extra + extra,
    )

/** Both relations to one card: every relation type either names. */
private fun Relation.joinedWith(other: Relation): Relation =
    copy(relation = relation.joinedWith(other.relation), extra = other.extra + extra)

/** Every key of both maps; where both have a key, [join] combines the values, this map's first. */
private fun <K, V> Map<K, V>?.joinedWith(
    other: Map<K, V>?,
    join: (V, V) -> V = { own, _ -> own },
): Map<K, V>? =
    if (this == null) {
        other
    } else {
        other.orEmpty().entries.fold(this) { joined, (key, value) ->
            joined + (key to (joined[key]?.let { join(it, value) } ?: value))
        }
    }

private fun <T> Map<String, T>?.unitedWith(other: Map<String, T>?): Map<String, T>? =
    other
        .orEmpty()
        .entries
        .filterNot { (_, value) -> this?.containsValue(value) == true }
        .fold(this ?: other?.let { emptyMap() }) { united, (key, value) ->
            val taken = united.orEmpty().keys
            united.orEmpty() + ((key.takeUnless { it in taken } ?: freeKeys("$key-", taken).first()) to value)
        }
