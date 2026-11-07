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
 * The scope's header over the pinned mailboxes and the user's shared folders, in the order and
 * layout the user chose. Tapping the header calls [onPickScope]. The [footer] stays at the bottom.
 */
@Composable
internal fun MailSidebar(
    state: SidebarState,
    current: SidebarPin?,
    content: SidebarContent,
    footer: @Composable () -> Unit,
    onPickScope: () -> Unit,
    onSettings: () -> Unit,
    onSelect: (SidebarPin) -> Unit,
) {
    val folders = content.folders
    val layout = content.layout
    val inbox = content.inbox
    val mailboxes = remember(folders, inbox, layout) { sidebarMailboxes(folders, inbox, layout) }
    val shared = remember(folders) { sharedFolders(folders) }
    val pick = rememberSidebarPick(current, mailboxes, onSelect)
    val tree = rememberFolderTree(pick, content.counter, content.colorOf, content.onFolderActions)
    Sidebar(
        state,
        title = content.header.title,
        header = {
            SidebarHeader(
                content.header.title,
                content.header.avatars,
                pickLabel = "Show mail from",
                onPick = onPickScope,
                onSettings = onSettings,
                subtitle = content.header.subtitle,
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
            when (section) {
                SidebarSection.Mailboxes -> {
                    SidebarMailboxList(
                        mailboxes,
                        layout.mailboxes,
                        pick.inMailboxes,
                        content.counter,
                        content.colorOf,
                        pick::selectInMailboxes,
                    )
                }

                SidebarSection.Folders -> {
                    tree.Rows(shared)
                }
            }
        }
    }
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
    current: SidebarPin?,
    mailboxes: List<SidebarMailbox>,
    onSelect: (SidebarPin) -> Unit,
): SidebarPick {
    val pickedInTree = rememberSaveable { mutableStateOf(false) }
    return SidebarPick(current, mailboxes.any { it.pin == current }, pickedInTree, onSelect)
}

/**
 * A pinned folder shows both as a mailbox and in the folder tree; only the list the user tapped it
 * in marks it selected, and the mailbox wins when the page opened some other way.
 */
private class SidebarPick(
    current: SidebarPin?,
    pinned: Boolean,
    private val pickedInTree: MutableState<Boolean>,
    private val onSelect: (SidebarPin) -> Unit,
) {
    private val inBoth = pinned && current is SidebarPin.Folder
    val inTree: SidebarPin? = current.takeUnless { inBoth && !pickedInTree.value }
    val inMailboxes: SidebarPin? = current.takeUnless { inBoth && pickedInTree.value }

    fun selectInTree(page: SidebarPin) {
        pickedInTree.value = true
        onSelect(page)
    }

    fun selectInMailboxes(page: SidebarPin) {
        pickedInTree.value = false
        onSelect(page)
    }
}
