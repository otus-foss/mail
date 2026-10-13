package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.components.Sidebar
import com.otus.designsystem.components.SidebarState
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.SidebarHeading
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_create_new_folder
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.inbox.domain.SidebarCount
import com.otus.mail.feature.inbox.domain.SidebarSection
import com.otus.mail.feature.inbox.presentation.label
import com.otus.mail.feature.inbox.presentation.title
import com.otus.mail.feature.updates.domain.NightlyBuild
import com.otus.mail.feature.updates.presentation.components.NightlyUpdateEntry

/**
 * The account rail beside the built-in mailboxes and the user's shared folders, in the order and
 * layout the user chose. Choosing an account in the rail, or All, changes only the counts:
 * [accountId] is the chosen account, or null for every account. Tapping All again opens the
 * inbox of every account. A newer nightly [update], when there is one, leads the list.
 */
@Composable
internal fun MailSidebar(
    state: SidebarState,
    current: MailPage,
    states: MailStates,
    accountId: String?,
    update: NightlyBuild?,
    onSelectAccount: (String?) -> Unit,
    onSelect: (MailPage) -> Unit,
) {
    val accounts = states.accounts.accounts
    val folders = states.folders.accounts
    val layout = states.behavior.behavior.sidebar
    val account = accounts.firstOrNull { it.id == accountId }
    val counter = SidebarCounter(account?.id, layout.counts)
    var collapsedSections by rememberSaveable(stateSaver = SectionsSaver) { mutableStateOf(emptySet<SidebarSection>()) }
    var collapsedFolders by rememberSaveable(stateSaver = KeysSaver) { mutableStateOf(emptySet<List<String>>()) }
    val mailboxes = remember(folders) { sidebarMailboxes(folders) }
    val shared = remember(folders) { sharedFolders(folders) }
    val inboxes = folders.mapNotNull { it.inbox }
    Sidebar(
        state,
        title = account?.name ?: "All accounts",
        subtitle =
            account?.username
                ?: allAccountsSummary(accounts.size, SidebarCounter(null, layout.counts).of(inboxes), layout.counts),
        rail = { MailSidebarRail(accounts, inboxes, layout.counts, account?.id, onSelectAccount, onSelect) },
    ) {
        update?.let { NightlyUpdateEntry(it) }
        layout.shown.forEach { section ->
            val open = section !in collapsedSections
            SidebarHeading(
                section.title,
                expanded = open,
                onExpandedChange = {
                    collapsedSections =
                        if (it) collapsedSections - section else collapsedSections + section
                },
                action =
                    if (section == SidebarSection.Folders) {
                        { NewFolderButton { onSelect(MailPage.Folders) } }
                    } else {
                        null
                    },
            )
            if (open) {
                when (section) {
                    SidebarSection.Mailboxes -> {
                        SidebarMailboxList(mailboxes, layout.mailboxes, current, counter, onSelect)
                    }

                    SidebarSection.Folders -> {
                        SidebarFolderList(
                            shared,
                            collapsedFolders,
                            onCollapse = { key, collapse ->
                                collapsedFolders =
                                    if (collapse) collapsedFolders + setOf(key) else collapsedFolders - setOf(key)
                            },
                            current,
                            counter,
                            onSelect,
                        )
                    }
                }
            }
        }
    }
}

/** Folders are made per account, so this opens the folder settings, where each account has its own. */
@Composable
private fun NewFolderButton(onClick: () -> Unit) {
    IconButton("New folder", onClick, outlined = false) { Symbol(Res.drawable.symbol_create_new_folder) }
}

/** The account count, then the inboxes' [count] in the user's chosen [counts], when there is one. */
private fun allAccountsSummary(
    accounts: Int,
    count: String?,
    counts: SidebarCount,
): String =
    when (accounts) {
        0 -> "No accounts yet"
        1 -> "1 account"
        else -> "$accounts accounts"
    } + count?.let { " · $it ${counts.label.lowercase()}" }.orEmpty()

private val SectionsSaver =
    listSaver<Set<SidebarSection>, String>(
        save = { sections -> sections.map { it.storageValue } },
        restore = { saved ->
            saved
                .mapNotNull { value ->
                    SidebarSection.entries.firstOrNull { it.storageValue == value }
                }.toSet()
        },
    )

// Each key is saved as its length, then its levels, so no character in a folder name can split it.
private val KeysSaver =
    listSaver<Set<List<String>>, String>(
        save = { keys -> keys.flatMap { listOf(it.size.toString()) + it } },
        restore = { saved ->
            buildSet {
                var index = 0
                while (index < saved.size) {
                    val size = saved[index].toInt()
                    add(saved.subList(index + 1, index + 1 + size).toList())
                    index += size + 1
                }
            }
        },
    )
