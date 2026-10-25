package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ContactHeader
import com.otus.designsystem.components.ContactRow
import com.otus.designsystem.components.ContactValueRow
import com.otus.designsystem.components.InfoStrip
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.RowGroup
import com.otus.designsystem.components.StripAction
import com.otus.designsystem.elements.SectionCaption
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_cake
import com.otus.designsystem.resources.symbol_call
import com.otus.designsystem.resources.symbol_cloud_off
import com.otus.designsystem.resources.symbol_drive_file_move
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_notes
import com.otus.designsystem.theme.PageTokens
import com.otus.jscontact.AnniversaryKind
import com.otus.jscontact.PartialDate
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.CardSync
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.ContactValue
import com.otus.mail.feature.contacts.domain.ContactWriteFailure
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.icon
import com.otus.mail.feature.contacts.presentation.owner
import com.otus.mail.feature.contacts.presentation.text

/** What one person's page shows besides the header: their books, groups, and a refused change. */
internal class PersonFacts(
    val person: ContactPerson,
    val booksOf: (ContactCard) -> List<ContactBook>,
    val groups: List<ContactCard>,
    val saveElsewhere: ContactBook?,
)

/** A person's values in raised groups: addresses, then numbers and dates, then where it is saved. */
@Composable
internal fun ContactDetails(
    facts: PersonFacts,
    onWriteTo: (String) -> Unit,
    onOpen: (CardKey) -> Unit,
    onSaveElsewhere: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val person = facts.person
    val main = person.main
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
        (person.cards.firstNotNullOfOrNull { (it.sync as? CardSync.Refused)?.reason })?.let { reason ->
            InfoStrip(
                Res.drawable.symbol_cloud_off,
                "Not saved",
                reason.text(),
                facts.saveElsewhere?.let {
                    StripAction(
                        "Save to ${it.name}",
                        Res.drawable.symbol_drive_file_move,
                        onSaveElsewhere,
                    )
                },
            )
        }
        ContactHeader(person.avatar, listOfNotNull(main.role))
        if (person.emails.isNotEmpty()) {
            SectionCaption("Email")
            // With several addresses, the primary one, which Mail fills in first, stands apart.
            val primary = person.emails.first().takeIf { person.emails.size > 1 }
            val rows = { emails: List<ContactValue>, caption: String? ->
                emails.map { email ->
                    @Composable {
                        ContactValueRow(
                            email.value,
                            caption,
                            label = email.label.text,
                            highlighted = caption != null,
                            onClick = { onWriteTo(email.value) },
                        )
                    }
                }
            }
            primary?.let {
                RowGroup(rows(listOf(it), "Primary"), raised = true, highlighted = true)
            }
            RowGroup(rows(person.emails - setOfNotNull(primary), null), raised = true)
        }
        val other = otherRows(person, facts.groups)
        if (other.isNotEmpty()) RowGroup(other, raised = true)
        SectionCaption("Saved in")
        RowGroup(
            person.cards.flatMap { card -> facts.booksOf(card).map { card to it } }.map { (card, book) ->
                {
                    ContactValueRow(
                        book.name,
                        listOfNotNull(book.owner(), "Read-only".takeUnless { book.canWrite }).joinToString(" · "),
                        icon = book.icon,
                        onClick = { onOpen(card.key) }.takeIf { person.cards.size > 1 },
                    )
                }
            },
            raised = true,
        )
        if (person.cards.size > 1) {
            PageNote("Shown as one person. The cards stay separate on your server.")
        }
    }
}

private fun otherRows(
    person: ContactPerson,
    groups: List<ContactCard>,
): List<@Composable () -> Unit> =
    buildList {
        person.cards.flatMap { it.phones }.distinctBy { it.value }.forEach { phone ->
            add { ContactValueRow(phone.value, null, icon = Res.drawable.symbol_call, label = phone.label.text) }
        }
        person.cards
            .firstNotNullOfOrNull { card ->
                card.card.anniversaries
                    ?.values
                    ?.firstOrNull { it.kind == AnniversaryKind.Birth }
                    ?.date as? PartialDate
            }?.let { birthday -> add { ContactValueRow(birthday.text(), "Birthday", icon = Res.drawable.symbol_cake) } }
        if (groups.isNotEmpty()) {
            add {
                ContactValueRow(
                    groups.joinToString(", ") { it.displayName },
                    "Groups",
                    icon = Res.drawable.symbol_group,
                )
            }
        }
        person.cards
            .firstNotNullOfOrNull {
                it.card.notes
                    ?.values
                    ?.firstOrNull()
                    ?.note
            }?.let { note ->
                add { ContactValueRow(note, "Note", icon = Res.drawable.symbol_notes) }
            }
    }

/** A group's members, each opening their own page. */
@Composable
internal fun GroupMembers(
    group: ContactCard,
    members: List<ContactPerson>,
    onOpen: (CardKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ContactHeader(group.avatar, listOf(if (members.size == 1) "1 person" else "${members.size} people"))
        members.forEach { person ->
            ContactRow(
                person.name,
                person.emails.firstOrNull()?.value ?: "No address",
                person.avatar,
                { onOpen(person.main.key) },
            )
        }
    }
}

private fun ContactWriteFailure.text(): String =
    when (this) {
        ContactWriteFailure.ReadOnly -> "This address book stopped allowing changes."
        ContactWriteFailure.Refused -> "The server refused the change."
        ContactWriteFailure.Offline -> "It syncs when you’re back online."
    }
