package com.otus.mail.feature.contacts.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactAddress

/** Where the Contacts page leads. */
internal class ContactsNavigation(
    val onOpenNavigation: () -> Unit,
    val onOpen: (CardKey) -> Unit,
    val onNew: () -> Unit,
    val onWrite: (List<ContactAddress>) -> Unit,
)
