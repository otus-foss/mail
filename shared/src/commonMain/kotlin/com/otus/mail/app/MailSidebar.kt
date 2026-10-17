package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.components.ActionDialog
import com.otus.designsystem.components.DialogAction
import com.otus.designsystem.components.Sidebar
import com.otus.designsystem.components.SidebarHeader
import com.otus.designsystem.components.SidebarState
import com.otus.designsystem.elements.SidebarDivider
import com.otus.mail.feature.folders.domain.SharedFolder
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.folders.presentation.components.FolderColorSheet
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.SidebarSection
import com.otus.mail.feature.inbox.presentation.scopeHeader
import com.otus.mail.feature.inbox.presentation.title
import com.otus.mail.feature.updates.domain.NightlyBuild
import com.otus.mail.feature.updates.presentation.components.NightlyUpdateEntry

/**
 * The chosen [scope] in the header, over the built-in mailboxes and the user's shared folders in
 * the order and layout the user chose. Every count follows the scope's account. Tapping the header
 * opens "Show mail from", which calls [onChooseScope]. A newer nightly [update], when there is
 * one, leads the list.
 */
@Composable
internal fun MailSidebar(
    state: SidebarState,
    current: MailPage,
    states: MailStates,
    scope: MailScopeChoice,
    update: NightlyBuild?,
    onChooseScope: (MailScopeChoice) -> Unit,
    onSelect: (MailPage) -> Unit,
) {
    val folders = states.folders.accounts
    val layout = states.behavior.behavior.sidebar
    val counter = SidebarCounter(scope.accountId, layout.counts)
    val header = scopeHeader(scope, states.accountAddresses())
    var picking by rememberSaveable { mutableStateOf(false) }
    var collapsedFolders by rememberSaveable(stateSaver = KeysSaver) { mutableStateOf(emptySet<List<String>>()) }
    val inbox = states.behavior.behavior.inbox
    val mailboxes = remember(folders, inbox) { sidebarMailboxes(folders, inbox) }
    val shared = remember(folders) { sharedFolders(folders) }
    val actions = rememberFolderActions(states)
    val tree =
        SidebarFolderTree(
            collapsedFolders,
            onCollapse = { key, collapse ->
                collapsedFolders = if (collapse) collapsedFolders + setOf(key) else collapsedFolders - setOf(key)
            },
            current,
            counter,
            colorOf = states.folderColors::groupColorOf,
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
    ) {
        update?.let { NightlyUpdateEntry(it) }
        layout.shown.forEachIndexed { index, section ->
            if (index > 0) SidebarDivider()
            when (section) {
                SidebarSection.Mailboxes -> {
                    SidebarMailboxList(mailboxes, layout.mailboxes, current, counter, onSelect)
                }

                SidebarSection.Folders -> {
                    tree.Rows(shared)
                }
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
