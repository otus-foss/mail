package com.otusfoss.mail.feature.sidebar.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.shared.sidebar.components.Sidebar
import com.otusfoss.core.designsystem.shared.sidebar.components.SidebarHeader
import com.otusfoss.core.designsystem.shared.sidebar.components.SidebarState
import com.otusfoss.core.designsystem.shared.sidebar.elements.SidebarDivider
import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.SharedFolder
import com.otusfoss.mail.core.mailbox.sharedFolders
import com.otusfoss.mail.core.messages.presentation.ScopeHeader
import com.otusfoss.mail.core.settings.domain.InboxContents
import com.otusfoss.mail.core.settings.domain.SidebarLayout
import com.otusfoss.mail.core.settings.domain.SidebarPin
import com.otusfoss.mail.core.settings.domain.SidebarSection
import com.otusfoss.mail.feature.sidebar.presentation.KeysSaver
import com.otusfoss.mail.feature.sidebar.presentation.SidebarCounter
import com.otusfoss.mail.feature.sidebar.presentation.SidebarMailbox
import com.otusfoss.mail.feature.sidebar.presentation.sidebarMailboxes

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
