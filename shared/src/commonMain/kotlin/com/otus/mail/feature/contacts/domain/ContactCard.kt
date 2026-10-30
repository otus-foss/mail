package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.AnniversaryKind
import com.otus.jscontact.Card
import com.otus.jscontact.CardKind
import com.otus.jscontact.NameComponentKind
import com.otus.jscontact.PartialDate

/**
 * One contact card as the server holds it, kept whole so an edit changes only what the user
 * changed. [books] are the address books it is in. A card the device changed but has not sent yet
 * carries its [sync] state.
 */
data class ContactCard(
    val key: CardKey,
    val card: Card,
    val books: List<BookKey>,
    val sync: CardSync = CardSync.Synced,
) {
    val isGroup: Boolean get() = card.kind == CardKind.Group

    /** Whether the card names the person; without a name it goes by its address. */
    val hasName: Boolean
        get() {
            val name =
                card.name
                    ?.full
                    ?.trim()
                    .orEmpty()
                    .ifEmpty { "${card.givenName} ${card.surname}".trim() }
            // Some apps save the card's own address as its name; that is no name. Another address
            // as the name is still what the card is called, so it stays.
            return name.isNotEmpty() &&
                card.emails
                    .orEmpty()
                    .values
                    .none { it.address.equals(name, ignoreCase = true) }
        }

    /** The full name, or the joined name parts, or else the preferred address. */
    val displayName: String
        get() =
            card.name
                ?.full
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(card.givenName, card.surname).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { null }
                ?: emails.firstOrNull()?.value
                ?: "No name"

    /**
     * Addresses with the preferred one first (RFC 9553, section 1.5.3: lower `pref` wins). The
     * first is the one Mail fills in, so it counts as preferred once there is a choice.
     */
    val emails: List<ContactValue>
        get() {
            val all = card.emails.orEmpty()
            return all
                .sortedByPref { it.pref }
                .mapIndexed { index, (id, email) ->
                    ContactValue(
                        id,
                        email.address,
                        ContactLabel.of(email.contexts, email.label),
                        index == 0 && all.size > 1,
                    )
                }
        }

    val phones: List<ContactValue>
        get() =
            card.phones
                .orEmpty()
                .sortedByPref { it.pref }
                .map { (id, phone) ->
                    ContactValue(id, phone.number, ContactLabel.of(phone.contexts, phone.label), preferred = false)
                }

    /** Title and organization, each once, such as "Rear admiral · US Navy". */
    val role: String?
        get() =
            listOfNotNull(card.titleName, card.organizationName)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .takeIf { it.isNotEmpty() }

    /** First and last initial of the name, or the first letter of the address. */
    val initials: String
        get() {
            val words = displayName.replace(".", "").split(' ').filter { it.isNotEmpty() }
            val letters =
                if (words.size > 1) "${words.first().first()}${words.last().first()}" else words.firstOrNull()?.take(1)
            return letters.orEmpty().uppercase()
        }

    /** The member uids of a group card. */
    val members: Set<String> get() =
        card.members
            .orEmpty()
            .filterValues { it }
            .keys
}

/** Whether a card's latest change reached the server. */
sealed interface CardSync {
    data object Synced : CardSync

    /** Saved on the device and waiting for a connection. */
    data object Waiting : CardSync

    /** The server refused the change; it stays on the device until the user decides. */
    data class Refused(
        val reason: ContactWriteFailure,
    ) : CardSync
}

/** One address or number of a card: its map [id], the [value], its [label], and whether it is [preferred]. */
data class ContactValue(
    val id: String,
    val value: String,
    val label: ContactLabel,
    val preferred: Boolean,
)

/** The entries in [pref] order (RFC 9553, section 1.5.3: lower wins, none last), then by key. */
private fun <T> Map<String, T>.sortedByPref(pref: (T) -> Int?): List<Map.Entry<String, T>> =
    entries.sortedWith(compareBy({ pref(it.value) ?: Int.MAX_VALUE }, { it.key }))

internal val Card.givenName: String
    get() =
        name
            ?.components
            .orEmpty()
            .filter { it.kind == NameComponentKind.Given }
            .joinToString(" ") { it.value }

internal val Card.surname: String
    get() =
        name
            ?.components
            .orEmpty()
            .filter { it.kind == NameComponentKind.Surname }
            .joinToString(" ") { it.value }

/** The name of the card's first organization. */
internal val Card.organizationName: String?
    get() =
        organizations
            ?.values
            ?.firstOrNull()
            ?.name

/** The name of the card's first title. */
internal val Card.titleName: String?
    get() =
        titles
            ?.values
            ?.firstOrNull()
            ?.name

/** The card's birthday, when it has a date that is not a timestamp. */
internal val Card.birthday: PartialDate?
    get() =
        anniversaries
            ?.values
            ?.firstOrNull { it.kind == AnniversaryKind.Birth }
            ?.date as? PartialDate

/** The text of the card's first note. */
internal val Card.firstNote: String?
    get() =
        notes
            ?.values
            ?.firstOrNull()
            ?.note
