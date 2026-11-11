package com.otusfoss.mail.feature.contacts.presentation

import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactAddress

internal class ContactsNavigation(
    val onOpenNavigation: () -> Unit,
    val onOpen: (CardKey) -> Unit,
    val onNew: () -> Unit,
    val onWrite: (List<ContactAddress>) -> Unit,
)
