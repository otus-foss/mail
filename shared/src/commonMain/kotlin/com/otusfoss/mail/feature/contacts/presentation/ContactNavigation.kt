package com.otusfoss.mail.feature.contacts.presentation

import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactAddress

internal class ContactNavigation(
    val onBack: () -> Unit,
    val onEdit: (CardKey) -> Unit,
    val onOpen: (CardKey) -> Unit,
    val onWrite: (List<ContactAddress>) -> Unit,
)
