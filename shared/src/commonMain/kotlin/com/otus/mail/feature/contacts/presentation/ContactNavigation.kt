package com.otus.mail.feature.contacts.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.components.ActionBar
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.BarAction
import com.otus.designsystem.components.BarContext
import com.otus.designsystem.components.BarToken
import com.otus.designsystem.components.ChipPlacement
import com.otus.designsystem.components.ConfirmDialog
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.components.MenuAction
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.IconButtonLook
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_call_merge
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.resources.symbol_contacts
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_group_add
import com.otus.designsystem.resources.symbol_mail
import com.otus.designsystem.resources.symbol_person_edit
import com.otus.designsystem.resources.symbol_upload
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.CardSync
import com.otus.mail.feature.contacts.domain.ContactAddress
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.domain.photo
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.components.ContactDetails
import com.otus.mail.feature.contacts.presentation.components.GroupEdit
import com.otus.mail.feature.contacts.presentation.components.GroupEditSheet
import com.otus.mail.feature.contacts.presentation.components.GroupMembers
import com.otus.mail.feature.contacts.presentation.components.MergeSheet
import com.otus.mail.feature.contacts.presentation.components.PersonFacts
import com.otus.mail.feature.contacts.presentation.components.moreOptions
import com.otus.mail.feature.contacts.presentation.peopleCount
import com.otus.mail.feature.contacts.presentation.photoChoices
import com.otus.mail.feature.contacts.presentation.plainTextClip
import com.otus.mail.feature.contacts.presentation.removeFromGroup
import com.otus.mail.feature.contacts.presentation.setPhoto
import kotlinx.coroutines.launch

/** Where one person's page leads. */
internal class ContactNavigation(
    val onBack: () -> Unit,
    val onEdit: (CardKey) -> Unit,
    val onOpen: (CardKey) -> Unit,
    val onWrite: (List<ContactAddress>) -> Unit,
)
