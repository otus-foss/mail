package com.otus.mail.feature.contacts.domain

import com.otus.avatar.AVATAR_PHOTO_KEY
import com.otus.avatar.isAvatarCard
import com.otus.avatar.withoutAvatarStyle
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
        val emailKeys = valueKeys(keptEmails, "e", card.emails.orEmpty().keys)
        val phoneKeys = valueKeys(keptPhones, "p", card.phones.orEmpty().keys)
        return card
            .copy(
                name = name(card.name),
                emails =
                    keptEmails
                        .mapIndexed { index, value ->
                            val old = value.id?.let { id -> card.emails?.get(id) }
                            emailKeys[index] to
                                (old ?: EmailAddress(value.value.trim())).copy(
                                    address = value.value.trim(),
                                    contexts = value.label.contexts(old?.contexts),
                                    label = value.label.custom,
                                    pref = if (value.preferred && keptEmails.size > 1) 1 else null,
                                )
                        }.toMap()
                        .takeIf { it.isNotEmpty() },
                phones =
                    keptPhones
                        .mapIndexed { index, value ->
                            val old = value.id?.let { id -> card.phones?.get(id) }
                            phoneKeys[index] to
                                (old ?: Phone(value.value.trim())).copy(
                                    number = value.value.trim(),
                                    contexts = value.label.contexts(old?.contexts),
                                    label = value.label.custom,
                                )
                        }.toMap()
                        .takeIf { it.isNotEmpty() },
                organizations =
                    replaceFirst(card.organizations, organization) { old, text ->
                        old?.copy(name = text) ?: Organization(name = text)
                    },
                titles = replaceFirst(card.titles, title) { old, text -> old?.copy(name = text) ?: Title(text) },
                anniversaries =
                    card.anniversaries
                        .orEmpty()
                        .filterValues { it.kind != AnniversaryKind.Birth }
                        .plus(listOfNotNull(birthday?.let { BIRTHDAY to Anniversary(AnniversaryKind.Birth, it) }))
                        .takeIf { it.isNotEmpty() },
                notes = replaceFirst(card.notes, note) { old, text -> old?.copy(note = text) ?: Note(text) },
            ).let { if (photo == card.photo) it else it.withPhoto(photo) }
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
    }
}

/** The card's photo: the avatar photo on an avatar card, else its first. */
internal val Card.photo: Media?
    get() =
        media?.get(AVATAR_PHOTO_KEY)?.takeIf { it.kind == MediaKind.Photo }
            ?: media?.values?.firstOrNull { it.kind == MediaKind.Photo }

/**
 * This card with [photo] in place of every photo it had, so other apps show the same one; null
 * removes them. An avatar card stays one: the photo goes under the avatar key and replaces its
 * style, as the avatar settings save a photo.
 */
internal fun Card.withPhoto(photo: Media?): Card {
    val avatar = isAvatarCard
    val photos =
        media
            .orEmpty()
            .filterValues { it.kind != MediaKind.Photo }
            .plus(listOfNotNull(photo?.let { (if (avatar) AVATAR_PHOTO_KEY else PHOTO_KEY) to it }))
            .takeIf { it.isNotEmpty() }
    return copy(media = photos).let { if (avatar && photo != null) it.withoutAvatarStyle() else it }
}

private const val PHOTO_KEY = "photo"

/** One address or number in the editor; [id] is its key on the card, null for a new one. */
data class DraftValue(
    val id: String? = null,
    val value: String = "",
    val label: ContactLabel = ContactLabel.Other,
    val preferred: Boolean = false,
) {
    /** Filled in but not an address a server accepts, such as "ada" without a domain. */
    val isInvalidEmail: Boolean get() = value.isNotBlank() && !isEmailAddress(value.trim())
}

/**
 * The card key for each of [values]: its own, else the next key such as `e2n` that neither the
 * card's [taken] keys nor another value uses, so a new value never replaces one that stays.
 */
private fun valueKeys(
    values: List<DraftValue>,
    prefix: String,
    taken: Set<String>,
): List<String> {
    val fresh = freeKeys(prefix, taken + values.mapNotNull { it.id }, NEW_SUFFIX).iterator()
    return values.map { it.id ?: fresh.next() }
}

// New keys get a suffix so they rarely look like a key another app chose.
private const val NEW_SUFFIX = "n"

/**
 * [values] with its first entry set to [text] through [update], which gets the old entry so its
 * other properties stay, or removed when [text] is blank; the other entries stay.
 */
private fun <T> replaceFirst(
    values: Map<String, T>?,
    text: String?,
    update: (old: T?, text: String) -> T,
): Map<String, T>? {
    val first = values?.keys?.firstOrNull() ?: "1"
    val rest = values.orEmpty() - first
    val kept =
        text
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { mapOf(first to update(values?.get(first), it)) }
            .orEmpty()
    return (kept + rest).takeIf { it.isNotEmpty() }
}
