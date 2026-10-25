package com.otus.mail.feature.contacts.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.ConfirmDialog
import com.otus.designsystem.components.InputSheet
import com.otus.designsystem.components.LetterIndex
import com.otus.designsystem.components.rememberLetterIndexState
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.components.ContactList
import com.otus.mail.feature.contacts.presentation.components.ContactListActions
import com.otus.mail.feature.contacts.presentation.components.ContactListContent
import com.otus.mail.feature.contacts.presentation.components.ContactsEmpty
import com.otus.mail.feature.contacts.presentation.components.ContactsFilterPanel
import com.otus.mail.feature.contacts.presentation.components.ContactsListBar
import com.otus.mail.feature.contacts.presentation.components.ContactsPanel
import com.otus.mail.feature.contacts.presentation.components.ContactsSelectionBar
import com.otus.mail.feature.contacts.presentation.components.ImportSheet
import com.otus.mail.feature.contacts.presentation.components.ListBarActions
import com.otus.mail.feature.contacts.presentation.components.MergeSheet
import com.otus.mail.feature.contacts.presentation.components.SelectionBarActions
import com.otus.mail.feature.contacts.presentation.components.emptyKind
import com.otus.mail.feature.inbox.domain.MailSender

/** Where the Contacts page leads. */
internal class ContactsNavigation(
    val onOpenNavigation: () -> Unit,
    val onOpen: (CardKey) -> Unit,
    val onNew: () -> Unit,
    val onWrite: (List<MailSender>) -> Unit,
)
