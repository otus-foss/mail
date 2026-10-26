package com.otus.mail.feature.contacts.presentation

import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactAddress

/** Where one person's page leads. */
internal class ContactNavigation(
    val onBack: () -> Unit,
    val onEdit: (CardKey) -> Unit,
    val onOpen: (CardKey) -> Unit,
    val onWrite: (List<ContactAddress>) -> Unit,
)
