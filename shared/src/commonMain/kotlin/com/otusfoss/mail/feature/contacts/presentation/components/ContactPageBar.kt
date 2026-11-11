package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_delete
import com.otusfoss.core.designsystem.resources.symbol_edit
import com.otusfoss.core.designsystem.resources.symbol_group_add
import com.otusfoss.core.designsystem.resources.symbol_mail
import com.otusfoss.core.designsystem.resources.symbol_person_edit
import com.otusfoss.core.designsystem.resources.symbol_upload
import com.otusfoss.core.designsystem.shared.actionbar.components.ActionBar
import com.otusfoss.core.designsystem.shared.actionbar.components.BarAction
import com.otusfoss.core.designsystem.shared.actionbar.components.BarContext
import com.otusfoss.core.designsystem.shared.actionbar.components.BarToken
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.mail.feature.contacts.domain.ContactAddress
import com.otusfoss.mail.feature.contacts.presentation.ContactNavigation
import com.otusfoss.mail.feature.contacts.presentation.ContactPage
import com.otusfoss.mail.feature.contacts.presentation.ContactsState
import com.otusfoss.mail.feature.contacts.presentation.avatar
import com.otusfoss.mail.feature.contacts.presentation.peopleCount

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
        BarAction(
            UiAction("Write", Res.drawable.symbol_edit, enabled = recipients.isNotEmpty()) {
                navigation.onWrite(recipients)
            },
        ),
        secondary =
            moreOptions(
                if (page.card.isGroup) {
                    groupActions(onOverlay).takeIf { page.writable }.orEmpty()
                } else {
                    personActions(page, state, navigation, onOverlay)
                },
            ),
        context = if (page.card.isGroup) peopleContext(recipients.size) else personContext(page, navigation.onWrite),
    )
}

/** A group's menu, shown only when the group can change. */
private fun groupActions(onOverlay: (ContactOverlay) -> Unit): List<UiAction> =
    listOf(
        UiAction("Add people", Res.drawable.symbol_group_add) {
            onOverlay(ContactOverlay.EditGroup(GroupEdit.AddPeople))
        },
        UiAction("Rename", Res.drawable.symbol_person_edit) {
            onOverlay(ContactOverlay.EditGroup(GroupEdit.Rename))
        },
        UiAction("Delete", Res.drawable.symbol_delete) { onOverlay(ContactOverlay.Delete) },
    )

/** A person's menu: Edit and Delete when every card can change, Export when files are available. */
private fun personActions(
    page: ContactPage,
    state: ContactsState,
    navigation: ContactNavigation,
    onOverlay: (ContactOverlay) -> Unit,
): List<UiAction> =
    listOfNotNull(
        UiAction("Edit", Res.drawable.symbol_person_edit) {
            navigation.onEdit(page.card.key)
        }.takeIf { page.writable },
        UiAction("Export as vCard", Res.drawable.symbol_upload) {
            state.export(page.cards)
        }.takeIf { state.canTransfer },
        UiAction("Delete", Res.drawable.symbol_delete) {
            onOverlay(ContactOverlay.Delete)
        }.takeIf { page.writable },
    )

/** How many people Write reaches. */
internal fun peopleContext(count: Int): BarContext =
    BarContext(peopleCount(count), BarToken.Count(count), "to ${peopleCount(count)}")

/** The preferred address, with the others as choices; none when the person has no address. */
private fun personContext(
    page: ContactPage,
    onWrite: (List<ContactAddress>) -> Unit,
): BarContext? {
    val first = page.emails.firstOrNull() ?: return null
    return BarContext(
        first.value,
        BarToken.Person(page.person?.avatar ?: page.card.avatar),
        "to ${first.value}",
        choices =
            page.emails.drop(1).map { email ->
                UiAction("Write to ${email.value}", Res.drawable.symbol_mail) {
                    onWrite(listOf(ContactAddress(page.name, email.value)))
                }
            },
    )
}
