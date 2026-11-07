package com.otus.mail.feature.contacts.presentation

import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.avatarStyle
import com.otus.avatar.generatedAvatar
import com.otus.avatar.isAvatarCard
import com.otus.avatar.ui.presentation.toChipAvatar
import com.otus.dateformat.fullName
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_badge
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_work
import com.otus.designsystem.shared.contacts.components.ContactRowLabel
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.shared.people.components.Recipient
import com.otus.jscontact.PartialDate
import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.photo
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.DrawableResource

/** The card's look: its avatar style, else its own photo when inline, else initials colored by its first address. */
internal val ContactCard.look: ResolvedAvatar
    get() =
        contactLook(
            generatedAvatar(displayName, emails.firstOrNull()?.value ?: displayName),
            card.avatarStyle,
            card.photo?.inlineBytes(),
        )

internal val ContactCard.avatar: ChipAvatar get() = look.toChipAvatar()

/** A group's style or photo, else the group symbol, as the list and the group's page show it. */
internal val ContactCard.groupAvatar: ChipAvatar
    get() = look.takeIf { it.isLooked }?.toChipAvatar() ?: ChipAvatar("", 0, symbol = Res.drawable.symbol_group)

/** The person's look from the first card that has a style or a photo, else their main card's initials. */
internal val ContactPerson.avatar: ChipAvatar
    get() = cards.firstOrNull { it.look.isLooked }?.avatar ?: main.avatar

/** An address as a person pill shows it: the address, with the card's avatar. */
internal fun ContactCard.pill(address: String): Recipient = Recipient(address, avatar, address)

/** The icon of a book: a group for shared books, a briefcase for work, else a person. */
internal val ContactBook.icon: DrawableResource
    get() =
        when {
            isShared -> Res.drawable.symbol_group
            name.contains("work", ignoreCase = true) -> Res.drawable.symbol_work
            else -> Res.drawable.symbol_person
        }

/** The pill a shared book's card shows in the All list. */
internal fun ContactBook.rowLabel(): ContactRowLabel? = if (isShared) ContactRowLabel(name, icon) else null

/** Whose a book is, to tell books of the same name apart: "Shared by Jordan", or the login it comes from. */
internal fun ContactBook.owner(): String = sharedBy?.let { "Shared by $it" } ?: loginName

/** A birthday such as "9 December" or "9 December 1906". */
internal fun PartialDate.text(): String {
    val name = month?.let { Month.entries.getOrNull(it.toInt() - 1)?.fullName }
    return listOfNotNull(day?.toString(), name, year?.toString()).joinToString(" ")
}

/** People counted with their noun: "1 person", "3 people". */
internal fun peopleCount(count: Int): String = if (count == 1) "1 person" else "$count people"

/** A generated avatar for a name alone, such as a group's. */
internal fun nameAvatar(name: String): ChipAvatar = initialsAvatar(name, name)

/** Initials on a color picked from [address], as everywhere else in the app before a photo loads. */
internal fun initialsAvatar(
    name: String,
    address: String,
): ChipAvatar = ResolvedAvatar(generatedAvatar(name, address)).toChipAvatar()

/** Whether one of the person's cards holds the avatar of one of the user's [own] addresses. */
internal fun ContactPerson.isOwnAvatar(own: Set<EmailAddressKey>): Boolean =
    cards.any { it.card.isAvatarCard } && emails.any { EmailAddressKey(it.value) in own }

/** The pill on a contact list row of a card that holds one of the user's own avatars. */
internal val AvatarLabel = ContactRowLabel("You", Res.drawable.symbol_badge)
