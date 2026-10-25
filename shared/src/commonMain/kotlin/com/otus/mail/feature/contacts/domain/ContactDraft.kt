package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Anniversary
import com.otus.jscontact.AnniversaryKind
import com.otus.jscontact.Card
import com.otus.jscontact.CardKind
import com.otus.jscontact.EmailAddress
import com.otus.jscontact.Media
import com.otus.jscontact.MediaKind
import com.otus.jscontact.Name
import com.otus.jscontact.NameComponent
import com.otus.jscontact.NameComponentKind
import com.otus.jscontact.Note
import com.otus.jscontact.Organization
import com.otus.jscontact.PartialDate
import com.otus.jscontact.Phone
import com.otus.jscontact.Title
import com.otus.jscontact.isEmailAddress

/**
 * What the contact editor changes. Turning it back into a card with [applyTo] keeps every
 * property the editor does not show, such as photos, addresses on the card that were left alone,
 * and vendor properties.
 */
data class ContactDraft(
    val given: String = "",
    val surname: String = "",
    val emails: List<DraftValue> = listOf(DraftValue()),
    val phones: List<DraftValue> = emptyList(),
    val organization: String? = null,
    val title: String? = null,
    val birthday: PartialDate? = null,
    val note: String? = null,
    val photo: Media? = null,
) {
    /** A draft is worth saving once it names the person or has an address, and every address it has is valid. */
    val canSave: Boolean
        get() =
            (given.isNotBlank() || surname.isNotBlank() || emails.any { it.value.isNotBlank() }) &&
                emails.none { it.isInvalidEmail }

    val fullName: String get() = listOf(given, surname).map(String::trim).filter(String::isNotEmpty).joinToString(" ")

    /** [card] with the draft's values; [card] is an empty individual for a new contact. */
    fun applyTo(card: Card = Card(kind = CardKind.Individual)): Card {
        val keptEmails = emails.filter { it.value.isNotBlank() }
        val keptPhones = phones.filter { it.value.isNotBlank() }
        return card.copy(
            name = name(card.name),
            emails =
                keptEmails
                    .mapIndexed { index, it ->
                        val old = it.id?.let { id -> card.emails?.get(id) }
                        it.key(index, "e") to
                            (old ?: EmailAddress(it.value.trim())).copy(
                                address = it.value.trim(),
                                contexts = it.label.contexts(old?.contexts),
                                label = it.label.custom,
                                pref = if (it.preferred && keptEmails.size > 1) 1 else null,
                            )
                    }.toMap()
                    .takeIf { it.isNotEmpty() },
            phones =
                keptPhones
                    .mapIndexed { index, it ->
                        val old = it.id?.let { id -> card.phones?.get(id) }
                        it.key(index, "p") to
                            (old ?: Phone(it.value.trim())).copy(
                                number = it.value.trim(),
                                contexts = it.label.contexts(old?.contexts),
                                label = it.label.custom,
                            )
                    }.toMap()
                    .takeIf { it.isNotEmpty() },
            organizations = replaceFirst(card.organizations, organization) { Organization(name = it) },
            titles = replaceFirst(card.titles, title) { Title(it) },
            anniversaries =
                card.anniversaries
                    .orEmpty()
                    .filterValues { it.kind != AnniversaryKind.Birth }
                    .plus(listOfNotNull(birthday?.let { BIRTHDAY to Anniversary(AnniversaryKind.Birth, it) }))
                    .takeIf { it.isNotEmpty() },
            notes = replaceFirst(card.notes, note) { Note(it) },
            media =
                if (photo == card.photo) {
                    card.media
                } else {
                    // A new or removed photo replaces every photo, so other apps show the same one.
                    card.media
                        .orEmpty()
                        .filterValues { it.kind != MediaKind.Photo }
                        .plus(listOfNotNull(photo?.let { PHOTO to it }))
                        .takeIf { it.isNotEmpty() }
                },
        )
    }

    private fun name(old: Name?): Name? {
        val parts =
            listOf(NameComponentKind.Given to given, NameComponentKind.Surname to surname)
                .filter { (_, value) -> value.isNotBlank() }
                .map { (kind, value) -> NameComponent(value.trim(), kind) }
        if (parts.isEmpty()) return null
        val others =
            old?.components.orEmpty().filter {
                it.kind != NameComponentKind.Given &&
                    it.kind != NameComponentKind.Surname
            }
        return (old ?: Name()).copy(
            components = parts + others,
            full = fullName,
            isOrdered = null,
            defaultSeparator = null,
        )
    }

    companion object {
        /** The editor's values for [card]: its first organization, title and note, and its birthday. */
        fun of(card: ContactCard): ContactDraft =
            ContactDraft(
                given =
                    card.card.givenName.ifEmpty {
                        card.card.name
                            ?.full
                            .orEmpty()
                            .takeIf { card.card.surname.isEmpty() }
                            .orEmpty()
                    },
                surname = card.card.surname,
                emails = card.emails.map { DraftValue(it.id, it.value, it.label, it.preferred) },
                phones = card.phones.map { DraftValue(it.id, it.value, it.label) },
                organization =
                    card.card.organizations
                        ?.values
                        ?.firstOrNull()
                        ?.name,
                title =
                    card.card.titles
                        ?.values
                        ?.firstOrNull()
                        ?.name,
                birthday =
                    card.card.anniversaries
                        ?.values
                        ?.firstOrNull { it.kind == AnniversaryKind.Birth }
                        ?.date as? PartialDate,
                note =
                    card.card.notes
                        ?.values
                        ?.firstOrNull()
                        ?.note,
                photo = card.card.photo,
            )

        private const val BIRTHDAY = "birthday"
        private const val PHOTO = "photo"
    }
}

/** The card's first photo. */
internal val Card.photo: Media? get() = media?.values?.firstOrNull { it.kind == MediaKind.Photo }

/** One address or number in the editor; [id] is its key on the card, null for a new one. */
data class DraftValue(
    val id: String? = null,
    val value: String = "",
    val label: ContactLabel = ContactLabel.Other,
    val preferred: Boolean = false,
) {
    /** Filled in but not an address a server accepts, such as "ada" without a domain. */
    val isInvalidEmail: Boolean get() = value.isNotBlank() && !isEmailAddress(value.trim())

    /** The card key to store it under: its own, else the next free one such as `e2`. */
    internal fun key(
        index: Int,
        prefix: String,
    ): String = id ?: "$prefix${index + 1}${NEW_SUFFIX}"
}

// New keys get a suffix so they never collide with a key the card already uses.
private const val NEW_SUFFIX = "n"

/**
 * [values] with its first entry set to [text], or removed when [text] is blank; the others stay.
 */
private fun <T> replaceFirst(
    values: Map<String, T>?,
    text: String?,
    make: (String) -> T,
): Map<String, T>? {
    val first = values?.keys?.firstOrNull() ?: "1"
    val rest = values.orEmpty() - first
    val kept =
        text
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { mapOf(first to make(it)) }
            .orEmpty()
    return (kept + rest).takeIf { it.isNotEmpty() }
}
