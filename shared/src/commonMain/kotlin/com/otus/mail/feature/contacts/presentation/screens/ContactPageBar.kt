package com.otus.mail.feature.contacts.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.ActionBar
import com.otus.designsystem.components.BarAction
import com.otus.designsystem.components.BarContext
import com.otus.designsystem.components.BarToken
import com.otus.designsystem.components.MenuAction
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_group_add
import com.otus.designsystem.resources.symbol_mail
import com.otus.designsystem.resources.symbol_person_edit
import com.otus.designsystem.resources.symbol_upload
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.components.GroupEdit
import com.otus.mail.feature.contacts.presentation.components.moreOptions
import com.otus.mail.feature.contacts.presentation.peopleCount
import com.otus.mail.feature.inbox.domain.MailSender

/**
 * The page's bar: Write to its recipients, the person's or group's other actions in the menu, and
 * a context naming who Write reaches. [onOverlay] opens a sheet or dialog.
 */
@Composable
internal fun ContactPageBar(
    page: ContactPage,
    state: ContactsState,
    navigation: ContactNavigation,
    onOverlay: (ContactOverlay) -> Unit,
) {
    val recipients = page.recipients
    ActionBar(
        BarAction("Write", Res.drawable.symbol_edit, enabled = recipients.isNotEmpty()) {
            navigation.onWrite(recipients)
        },
        secondary =
            moreOptions(
                if (page.card.isGroup) {
                    groupActions(onOverlay).takeIf { page.writable }.orEmpty()
                } else {
                    personActions(page, state, navigation, onOverlay)
                },
            ),
        context = if (page.card.isGroup) groupContext(recipients.size) else personContext(page, navigation.onWrite),
    )
}

/** A group's menu, shown only when the group can change. */
private fun groupActions(onOverlay: (ContactOverlay) -> Unit): List<MenuAction> =
    listOf(
        MenuAction("Add people", Res.drawable.symbol_group_add) {
            onOverlay(ContactOverlay.EditGroup(GroupEdit.AddPeople))
        },
        MenuAction("Rename", Res.drawable.symbol_person_edit) {
            onOverlay(ContactOverlay.EditGroup(GroupEdit.Rename))
        },
        MenuAction("Delete", Res.drawable.symbol_delete) { onOverlay(ContactOverlay.Delete) },
    )

/** A person's menu: Edit and Delete when every card can change, Export when files are available. */
private fun personActions(
    page: ContactPage,
    state: ContactsState,
    navigation: ContactNavigation,
    onOverlay: (ContactOverlay) -> Unit,
): List<MenuAction> =
    listOfNotNull(
        MenuAction("Edit", Res.drawable.symbol_person_edit) {
            navigation.onEdit(page.card.key)
        }.takeIf { page.writable },
        MenuAction("Export as vCard", Res.drawable.symbol_upload) {
            state.export(page.cards)
        }.takeIf { state.canTransfer },
        MenuAction("Delete", Res.drawable.symbol_delete) {
            onOverlay(ContactOverlay.Delete)
        }.takeIf { page.writable },
    )

/** How many members Write reaches. */
private fun groupContext(count: Int): BarContext =
    BarContext(peopleCount(count), BarToken.Count(count), "to ${peopleCount(count)}")

/** The preferred address, with the others as choices; none when the person has no address. */
private fun personContext(
    page: ContactPage,
    onWrite: (List<MailSender>) -> Unit,
): BarContext? {
    val first = page.emails.firstOrNull() ?: return null
    return BarContext(
        first.value,
        BarToken.Person(page.person?.avatar ?: page.card.avatar),
        "to ${first.value}",
        choices =
            page.emails.drop(1).map { email ->
                MenuAction("Write to ${email.value}", Res.drawable.symbol_mail) {
                    onWrite(listOf(MailSender(page.name, email.value)))
                }
            },
    )
}
