package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_cake
import com.otusfoss.core.designsystem.resources.symbol_call
import com.otusfoss.core.designsystem.resources.symbol_cloud_off
import com.otusfoss.core.designsystem.resources.symbol_drive_file_move
import com.otusfoss.core.designsystem.resources.symbol_group
import com.otusfoss.core.designsystem.resources.symbol_group_remove
import com.otusfoss.core.designsystem.resources.symbol_notes
import com.otusfoss.core.designsystem.shared.contacts.components.ContactEntry
import com.otusfoss.core.designsystem.shared.contacts.components.ContactHeader
import com.otusfoss.core.designsystem.shared.contacts.components.ContactValueRow
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.feedback.components.InfoStrip
import com.otusfoss.core.designsystem.shared.overlays.components.OptionsItem
import com.otusfoss.core.designsystem.shared.overlays.components.OptionsMenu
import com.otusfoss.core.designsystem.shared.pages.components.PageNote
import com.otusfoss.core.designsystem.shared.rows.components.EntryTaps
import com.otusfoss.core.designsystem.shared.rows.components.RowGroup
import com.otusfoss.core.designsystem.shared.rows.elements.SectionCaption
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.core.designsystem.theme.tokens.rows.EntryTokens
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.CardSync
import com.otusfoss.mail.feature.contacts.domain.ContactCard
import com.otusfoss.mail.feature.contacts.domain.ContactPerson
import com.otusfoss.mail.feature.contacts.domain.ContactWriteFailure
import com.otusfoss.mail.feature.contacts.domain.birthday
import com.otusfoss.mail.feature.contacts.domain.firstNote
import com.otusfoss.mail.feature.contacts.presentation.avatar
import com.otusfoss.mail.feature.contacts.presentation.formatPhone
import com.otusfoss.mail.feature.contacts.presentation.groupAvatar
import com.otusfoss.mail.feature.contacts.presentation.icon
import com.otusfoss.mail.feature.contacts.presentation.owner
import com.otusfoss.mail.feature.contacts.presentation.peopleCount
import com.otusfoss.mail.feature.contacts.presentation.text

/**
 * A person's values in titled groups: addresses; then numbers, birthday, groups, and note
 * together under Details, each marked by its icon; then where it is saved.
 */
@Composable
internal fun ContactDetails(
    facts: PersonFacts,
    onWriteTo: (String) -> Unit,
    onCopyNumber: (String) -> Unit,
    onOpen: (CardKey) -> Unit,
    onSaveElsewhere: () -> Unit,
    modifier: Modifier = Modifier,
    onEditAvatar: (() -> Unit)? = null,
) {
    val person = facts.person
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
        RefusedStrip(facts, onSaveElsewhere)
        ContactHeader(person.avatar, listOfNotNull(person.main.role), onEdit = onEditAvatar)
        if (facts.ownAvatar) PageNote("This is you. Mail shows this avatar wherever your address appears.")
        Section("Email", emailRows(person, onWriteTo))
        Section("Details", detailRows(facts, onCopyNumber))
        Section("Saved in", savedInRows(facts, onOpen))
        if (person.cards.size > 1) {
            PageNote("Shown as one person. The cards stay separate on your server.")
        }
    }
}

/** Says a change was not saved and why, offering another book when one can take the card. */
@Composable
private fun RefusedStrip(
    facts: PersonFacts,
    onSaveElsewhere: () -> Unit,
) {
    val reason = facts.person.cards.firstNotNullOfOrNull { (it.sync as? CardSync.Refused)?.reason } ?: return
    InfoStrip(
        Res.drawable.symbol_cloud_off,
        "Not saved",
        reason.text(),
        facts.saveElsewhere?.let {
            UiAction("Save to ${it.name}", Res.drawable.symbol_drive_file_move, onClick = onSaveElsewhere)
        },
    )
}

/** One row per address, each writing to it. */
private fun emailRows(
    person: ContactPerson,
    onWriteTo: (String) -> Unit,
): List<@Composable () -> Unit> {
    // With several addresses, the primary one, which Mail fills in first, stands out.
    val primary = person.emails.firstOrNull()?.takeIf { person.emails.size > 1 }
    return person.emails.map { email ->
        {
            ContactValueRow(
                email.value,
                "Primary".takeIf { email == primary },
                label = email.label.text,
                highlighted = email == primary,
                onClick = { onWriteTo(email.value) },
            )
        }
    }
}

/** Numbers, birthday, groups, and note, each marked by its icon; tapping a number copies it. */
private fun detailRows(
    facts: PersonFacts,
    onCopyNumber: (String) -> Unit,
): List<@Composable () -> Unit> {
    val cards = facts.person.cards.map { it.card }
    val birthday = cards.firstNotNullOfOrNull { it.birthday }
    val note = cards.firstNotNullOfOrNull { it.firstNote }
    return buildList {
        facts.person.cards.flatMap { it.phones }.distinctBy { it.value }.forEach { phone ->
            add {
                ContactValueRow(
                    formatPhone(phone.value),
                    null,
                    icon = Res.drawable.symbol_call,
                    label = phone.label.text,
                    onClick = { onCopyNumber(phone.value) },
                )
            }
        }
        if (birthday != null) add { ContactValueRow(birthday.text(), null, icon = Res.drawable.symbol_cake) }
        if (facts.groups.isNotEmpty()) {
            val names = facts.groups.joinToString(", ") { it.displayName }
            add { ContactValueRow(names, null, icon = Res.drawable.symbol_group) }
        }
        if (note != null) add { ContactValueRow(note, null, icon = Res.drawable.symbol_notes) }
    }
}

/** Each book holding a card; with several cards, tapping one opens that card alone. */
private fun savedInRows(
    facts: PersonFacts,
    onOpen: (CardKey) -> Unit,
): List<@Composable () -> Unit> {
    val person = facts.person
    return person.cards.flatMap { card -> facts.booksOf(card).map { card to it } }.map { (card, book) ->
        {
            ContactValueRow(
                book.name,
                listOfNotNull(book.owner(), "Read-only".takeUnless { book.canWrite }).joinToString(" · "),
                icon = book.icon,
                onClick = { onOpen(card.key) }.takeIf { person.cards.size > 1 },
            )
        }
    }
}

/** A [SectionCaption] over [rows] in a raised group; nothing without rows. */
@Composable
private fun Section(
    title: String,
    rows: List<@Composable () -> Unit>,
) {
    if (rows.isEmpty()) return
    SectionCaption(title)
    RowGroup(rows, raised = true)
}

/**
 * A group's members, each opening their own page. Holding one offers to take them out of the
 * group, through [onRemove], when the group can change. An empty group says how to fill it.
 */
@Composable
internal fun GroupMembers(
    group: ContactCard,
    members: List<ContactPerson>,
    onOpen: (CardKey) -> Unit,
    onRemove: ((ContactPerson) -> Unit)?,
    modifier: Modifier = Modifier,
    onEditAvatar: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
        ContactHeader(
            group.groupAvatar,
            listOf(peopleCount(members.size)),
            onEdit = onEditAvatar,
        )
        if (members.isEmpty()) {
            PageNote("No one in this group yet.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(EntryTokens.gap)) {
            members.forEach { person -> GroupMember(person, onOpen, onRemove) }
        }
    }
}

@Composable
private fun GroupMember(
    person: ContactPerson,
    onOpen: (CardKey) -> Unit,
    onRemove: ((ContactPerson) -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ContactEntry(
            person.name,
            person.emails.firstOrNull()?.value ?: "No address",
            person.avatar,
            EntryTaps({ onOpen(person.main.key) }, onRemove?.let { { open = true } }),
        )
        if (open && onRemove != null) {
            val remove =
                OptionsItem.Action(
                    UiAction("Remove from group", Res.drawable.symbol_group_remove, onClick = {
                        onRemove(person)
                    }),
                )
            OptionsMenu(listOf(remove)) { open = false }
        }
    }
}

private fun ContactWriteFailure.text(): String =
    when (this) {
        ContactWriteFailure.ReadOnly -> "This address book stopped allowing changes."
        ContactWriteFailure.Refused -> "The server refused the change."
        ContactWriteFailure.Offline -> "It syncs when you’re back online."
        ContactWriteFailure.Failing -> "The server kept failing to save the change."
    }
