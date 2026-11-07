package com.otus.mail.feature.contacts.presentation

import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactAddress

internal class ContactsNavigation(
    val onOpenNavigation: () -> Unit,
    val onOpen: (CardKey) -> Unit,
    val onNew: () -> Unit,
    val onWrite: (List<ContactAddress>) -> Unit,
)
