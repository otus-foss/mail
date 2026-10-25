package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.designsystem.components.ToastMessage
import com.otus.mail.feature.contacts.presentation.components.PersonContactSheet
import com.otus.mail.feature.contacts.presentation.screens.ContactEditorScreen
import com.otus.mail.feature.contacts.presentation.screens.ContactNavigation
import com.otus.mail.feature.contacts.presentation.screens.ContactScreen
import com.otus.mail.feature.contacts.presentation.screens.ContactsNavigation
import com.otus.mail.feature.contacts.presentation.screens.ContactsScreen
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.presentation.components.PersonSheet

/** The Read view's sheet for a person a message names: their card, or ways to save the address. */
internal fun contactSheet(
    states: MailStates,
    navigation: MailNavigation,
): PersonSheet =
    PersonSheet { person, onDismiss ->
        LaunchedEffect(Unit) { if (states.contacts.snapshot == null) states.contacts.refresh() }
        PersonContactSheet(
            person,
            states.contacts,
            onNew = {
                onDismiss()
                navigation.go(MailPage.ContactEditor())
            },
            onOpen = {
                onDismiss()
                navigation.go(MailPage.Contact(it))
            },
            onDismiss = onDismiss,
        )
    }

/** The Contacts page, one person, and the contact editor. Opening any of them loads the contacts. */
@Composable
internal fun ContactsPages(
    page: MailPage,
    states: MailStates,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    val contacts = states.contacts
    LaunchedEffect(Unit) { contacts.refresh() }
    LaunchedEffect(contacts.notice) {
        contacts.notice?.let { states.moveToasts.toasts.show(ToastMessage(it)) }
        contacts.notice = null
    }
    val write = { to: List<MailSender> -> navigation.go(MailPage.Compose(ComposeRequest.New(to = to), parent = page)) }
    when (page) {
        is MailPage.Contact -> {
            ContactScreen(
                contacts,
                page.key,
                ContactNavigation(
                    onBack = back,
                    onEdit = { navigation.go(MailPage.ContactEditor(it)) },
                    onOpen = { navigation.go(MailPage.Contact(it)) },
                    onWrite = write,
                ),
            )
        }

        is MailPage.ContactEditor -> {
            ContactEditorScreen(contacts, page.key, onClose = back) { saved ->
                navigation.go(if (page.key == null) MailPage.Contact(saved) else page.parent)
            }
        }

        else -> {
            ContactsScreen(
                contacts,
                ContactsNavigation(
                    onOpenNavigation = navigation.openSidebar,
                    onOpen = { navigation.go(MailPage.Contact(it)) },
                    onNew = { navigation.go(MailPage.ContactEditor()) },
                    onWrite = write,
                ),
            )
        }
    }
}
