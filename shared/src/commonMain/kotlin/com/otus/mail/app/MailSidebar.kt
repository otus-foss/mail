package com.otus.mail.app

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
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.settings.domain.SidebarSection
import com.otus.mail.core.settings.presentation.title
import com.otus.mail.feature.folders.domain.SharedFolder
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.folders.presentation.components.FolderColorSheet
import com.otus.mail.feature.inbox.presentation.SidebarCounter
import com.otus.mail.feature.inbox.presentation.scopeHeader

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
    val inbox = states.behavior.behavior.inbox
    val mailboxes = remember(folders, inbox, layout) { sidebarMailboxes(folders, inbox, layout) }
    val shared = remember(folders) { sharedFolders(folders) }
    val actions = rememberFolderActions(states)
    val colorOf = states.folderColors::groupColorOf
    val pick = rememberSidebarPick(current, mailboxes, onSelect)
    val tree = rememberFolderTree(pick, counter, colorOf, actions::open)
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
                SidebarSection.Mailboxes -> {
                    SidebarMailboxList(mailboxes, style, pick.inMailboxes, counter, colorOf, pick::selectInMailboxes)
                }

                SidebarSection.Folders -> {
                    tree.Rows(shared)
                }
            }
        }
    }
    if (picking) SidebarScopeSheet(states, scope, onChooseScope, onDismiss = { picking = false })
    actions.Sheets()
}

/** The folder tree, folding parents the user collapsed; [pick] marks its selected row. */
@Composable
private fun rememberFolderTree(
    pick: SidebarPick,
    counter: SidebarCounter,
    colorOf: (SharedFolder) -> String?,
    onActions: (SharedFolder) -> Unit,
): SidebarFolderTree {
    var collapsed by rememberSaveable(stateSaver = KeysSaver) { mutableStateOf(emptySet<List<String>>()) }
    return SidebarFolderTree(
        collapsed,
        onCollapse = { key, collapse -> collapsed = if (collapse) collapsed + setOf(key) else collapsed - setOf(key) },
        pick.inTree,
        counter,
        colorOf,
        pick::selectInTree,
        onActions = onActions,
    )
}

@Composable
private fun rememberSidebarPick(
    current: MailPage,
    mailboxes: List<SidebarMailbox>,
    onSelect: (MailPage) -> Unit,
): SidebarPick {
    val pickedInTree = rememberSaveable { mutableStateOf(false) }
    return SidebarPick(current, mailboxes.any { it.page == current }, pickedInTree, onSelect)
}

/**
 * A pinned folder shows both as a mailbox and in the folder tree; only the list the user tapped it
 * in marks it selected, and the mailbox wins when the page opened some other way.
 */
private class SidebarPick(
    current: MailPage,
    pinned: Boolean,
    private val pickedInTree: MutableState<Boolean>,
    private val onSelect: (MailPage) -> Unit,
) {
    private val inBoth = pinned && current is MailPage.Shared
    val inTree: MailPage? = current.takeUnless { inBoth && !pickedInTree.value }
    val inMailboxes: MailPage? = current.takeUnless { inBoth && pickedInTree.value }

    fun selectInTree(page: MailPage) {
        pickedInTree.value = true
        onSelect(page)
    }

    fun selectInMailboxes(page: MailPage) {
        pickedInTree.value = false
        onSelect(page)
    }
}

@Composable
private fun rememberFolderActions(states: MailStates): SidebarFolderActions =
    remember(states) { SidebarFolderActions(states) }

/** The sheet that colors a long-pressed folder. Only the user's own folders have colors. */
private class SidebarFolderActions(
    private val states: MailStates,
) {
    private var coloring by mutableStateOf<SharedFolder?>(null)

    fun open(folder: SharedFolder) {
        if (folder.folders.any(states.folderColors::colorable)) coloring = folder
    }

    @Composable
    fun Sheets() {
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
