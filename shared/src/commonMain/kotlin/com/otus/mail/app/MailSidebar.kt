package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.overlays.components.ActionDialog
import com.otus.designsystem.shared.overlays.components.DialogAction
import com.otus.designsystem.shared.sidebar.components.Sidebar
import com.otus.designsystem.shared.sidebar.components.SidebarHeader
import com.otus.designsystem.shared.sidebar.components.SidebarState
import com.otus.designsystem.shared.sidebar.elements.SidebarDivider
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.SharedFolder
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.folders.presentation.components.FolderColorSheet
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.SidebarSection
import com.otus.mail.feature.inbox.presentation.scopeHeader
import com.otus.mail.feature.inbox.presentation.title

/**
 * The chosen [scope] in the header, over the pinned mailboxes and the user's shared folders in
 * the order and layout the user chose. An account or address scope lists only its account's
 * mailboxes and folders, and every count follows the scope's account. Tapping the header
 * opens "Show mail from", which calls [onChooseScope]. The [footer] stays at the bottom.
 */
@Composable
internal fun MailSidebar(
    state: SidebarState,
    current: MailPage,
    states: MailStates,
    scope: MailScopeChoice,
    footer: @Composable () -> Unit,
    onChooseScope: (MailScopeChoice) -> Unit,
    onSelect: (MailPage) -> Unit,
) {
    val folders = states.folders.accounts.inScope(scope)
    val layout = states.behavior.behavior.sidebar
    val counter = SidebarCounter(scope.accountId, layout.counts)
    val header = scopeHeader(scope, states.accountAddresses())
    var picking by rememberSaveable { mutableStateOf(false) }
    var collapsedFolders by rememberSaveable(stateSaver = KeysSaver) { mutableStateOf(emptySet<List<String>>()) }
    val inbox = states.behavior.behavior.inbox
    val mailboxes = remember(folders, inbox, layout) { sidebarMailboxes(folders, inbox, layout) }
    val shared = remember(folders) { sharedFolders(folders) }
    val actions = rememberFolderActions(states)
    val colorOf = states.folderColors::groupColorOf
    val tree =
        SidebarFolderTree(
            collapsedFolders,
            onCollapse = { key, collapse ->
                collapsedFolders = if (collapse) collapsedFolders + setOf(key) else collapsedFolders - setOf(key)
            },
            current,
            counter,
            colorOf,
            onSelect,
            onActions = actions::open,
        )
    Sidebar(
        state,
        title = header.title,
        header = {
            SidebarHeader(
                header.title,
                header.avatars,
                pickLabel = "Show mail from",
                onPick = { picking = true },
                onSettings = { onSelect(MailPage.Settings) },
                subtitle = header.subtitle,
            )
        },
        footer = footer,
    ) {
        // An account without folders, or nothing pinned, would leave a divider over nothing.
        val empty =
            setOfNotNull(
                SidebarSection.Folders.takeIf { shared.isEmpty() },
                SidebarSection.Mailboxes.takeIf { mailboxes.isEmpty() },
            )
        (layout.shown - empty).forEach { section ->
            SidebarDivider()
            val style = layout.mailboxes
            when (section) {
                SidebarSection.Mailboxes -> SidebarMailboxList(mailboxes, style, current, counter, colorOf, onSelect)
                SidebarSection.Folders -> tree.Rows(shared)
            }
        }
    }
    if (picking) SidebarScopeSheet(states, scope, onChooseScope, onDismiss = { picking = false })
    actions.Dialogs()
}

@Composable
private fun rememberFolderActions(states: MailStates): SidebarFolderActions =
    remember(states) { SidebarFolderActions(states) }

/** A long-pressed folder's actions, then the sheet that colors it. Only the user's own folders have any. */
private class SidebarFolderActions(
    private val states: MailStates,
) {
    private var acting by mutableStateOf<SharedFolder?>(null)
    private var coloring by mutableStateOf<SharedFolder?>(null)

    fun open(folder: SharedFolder) {
        if (folder.folders.any(states.folderColors::colorable)) acting = folder
    }

    @Composable
    fun Dialogs() {
        acting?.let { folder ->
            ActionDialog(
                folder.name,
                listOf(DialogAction("Color", { coloring = folder })),
                onDismiss = { acting = null },
            )
        }
        coloring?.let { folder ->
            FolderColorSheet(
                current = states.folderColors.colors.of(folder),
                onPick = { states.folderColors.setColor(folder.folders, it) },
                onDismiss = { coloring = null },
            )
        }
    }
}

/** The folders of [scope]'s account, or every account's while it shows all mail. */
internal fun List<AccountFolders>.inScope(scope: MailScopeChoice): List<AccountFolders> =
    scope.accountId?.let { id -> filter { it.accountId == id } } ?: this
