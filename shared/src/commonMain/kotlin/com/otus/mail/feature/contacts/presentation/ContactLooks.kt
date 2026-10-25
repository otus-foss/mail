package com.otus.mail.feature.contacts.presentation

import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.components.ContactRowLabel
import com.otus.designsystem.components.OptionsItem
import com.otus.designsystem.components.Recipient
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_hide_image
import com.otus.designsystem.resources.symbol_image
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_work
import com.otus.jscontact.Media
import com.otus.jscontact.PartialDate
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.photo
import com.otus.mail.feature.inbox.presentation.components.chipAvatar
import org.jetbrains.compose.resources.DrawableResource
import kotlin.io.encoding.Base64

/** The card's own photo when it carries one inline, else initials colored by its first address. */
internal val ContactCard.avatar: ChipAvatar
    get() {
        val generated = chipAvatar(null, displayName, emails.firstOrNull()?.value ?: displayName)
        return generated.withPhoto(card.photo)
    }

/** A group's own photo, else the group symbol, as the list and the group's page show it. */
internal val ContactCard.groupAvatar: ChipAvatar
    get() = ChipAvatar("", 0, symbol = Res.drawable.symbol_group).withPhoto(card.photo)

internal val ContactPerson.avatar: ChipAvatar
    get() = cards.firstOrNull { it.avatar.imageBytes != null }?.avatar ?: main.avatar

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
    val name = month?.let { MONTHS.getOrNull(it.toInt() - 1) }
    return listOfNotNull(day?.toString(), name, year?.toString()).joinToString(" ")
}

private val MONTHS =
    listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    )

/** People counted with their noun: "1 person", "3 people". */
internal fun peopleCount(count: Int): String = if (count == 1) "1 person" else "$count people"

/** A generated avatar for a name alone, such as a group's. */
internal fun nameAvatar(name: String): ChipAvatar = chipAvatar(null, name, name)
