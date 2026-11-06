package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.feature.contacts.domain.ContactAddress
import com.otus.mail.feature.contacts.presentation.ContactNavigation
import com.otus.mail.feature.contacts.presentation.ContactsNavigation
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.components.PersonContactSheet
import com.otus.mail.feature.contacts.presentation.screens.ContactEditorScreen
import com.otus.mail.feature.contacts.presentation.screens.ContactScreen
import com.otus.mail.feature.contacts.presentation.screens.ContactsScreen
import com.otus.mail.feature.read.presentation.components.PersonSheet

/** The Read view's sheet for a person a message names: their card, or ways to save the address. */
internal fun contactSheet(
    contacts: ContactsState,
    navigation: MailNavigation,
): PersonSheet =
    PersonSheet { person, onDismiss ->
        LaunchedEffect(Unit) { if (contacts.snapshot == null) contacts.refresh() }
        PersonContactSheet(
            ContactAddress(person.name, person.address),
            contacts,
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
    contacts: ContactsState,
    toasts: ToastState,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    LaunchedEffect(Unit) { contacts.refresh() }
    LaunchedEffect(contacts.notice) {
        val notice = contacts.notice
        if (notice != null) toasts.show(ToastMessage(notice))
        contacts.notice = null
    }
    val write = { to: List<ContactAddress> ->
        val recipients = to.map { MailSender(it.name, it.address) }
        navigation.go(MailPage.Compose(ComposeRequest.New(to = recipients), parent = page))
    }
    when (page) {
        is MailPage.Contact -> ContactPage(page, contacts, navigation, back, write)
        is MailPage.ContactEditor -> ContactEditorPage(page, contacts, navigation, back)
        else -> ContactListPage(contacts, navigation, write)
    }
}

@Composable
private fun ContactPage(
    page: MailPage.Contact,
    contacts: ContactsState,
    navigation: MailNavigation,
    back: () -> Unit,
    write: (List<ContactAddress>) -> Unit,
) {
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

@Composable
private fun ContactEditorPage(
    page: MailPage.ContactEditor,
    contacts: ContactsState,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    ContactEditorScreen(contacts, page.key, onClose = back) { saved ->
        navigation.go(if (page.key == null) MailPage.Contact(saved) else page.parent)
    }
}

@Composable
private fun ContactListPage(
    contacts: ContactsState,
    navigation: MailNavigation,
    write: (List<ContactAddress>) -> Unit,
) {
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
