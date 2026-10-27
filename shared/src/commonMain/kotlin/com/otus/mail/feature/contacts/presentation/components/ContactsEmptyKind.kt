package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_contacts
import com.otus.designsystem.resources.symbol_person_add
import com.otus.designsystem.resources.symbol_person_search
import com.otus.designsystem.shared.feedback.components.EmptyState
import com.otus.mail.feature.contacts.presentation.ContactsState

/** Why the list has nothing to show. */
internal enum class ContactsEmptyKind { Loading, NoContacts, Empty, NoMatches }
