package com.otus.mail.feature.sidebar.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.sidebar.components.Sidebar
import com.otus.designsystem.shared.sidebar.components.SidebarHeader
import com.otus.designsystem.shared.sidebar.components.SidebarState
import com.otus.designsystem.shared.sidebar.elements.SidebarDivider
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.SharedFolder
import com.otus.mail.core.mailbox.sharedFolders
import com.otus.mail.core.messages.presentation.ScopeHeader
import com.otus.mail.core.settings.domain.InboxContents
import com.otus.mail.core.settings.domain.SidebarLayout
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.core.settings.domain.SidebarSection
import com.otus.mail.feature.sidebar.presentation.KeysSaver
import com.otus.mail.feature.sidebar.presentation.SidebarCounter
import com.otus.mail.feature.sidebar.presentation.SidebarMailbox
import com.otus.mail.feature.sidebar.presentation.sidebarMailboxes

/**
 * What the sidebar shows for the chosen "Show mail from" scope: its [folders], the user's
 * [layout], the folders the Inbox lists ([inbox]), the scope's [header], the [counter] beside each
 * mailbox, each shared folder's color, and what a long press on a folder opens.
 */
internal class SidebarContent(
    val folders: List<AccountFolders>,
    val layout: SidebarLayout,
    val inbox: InboxContents,
    val header: ScopeHeader,
    val counter: SidebarCounter,
    val colorOf: (SharedFolder) -> String?,
    val onFolderActions: (SharedFolder) -> Unit,
)
