package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_menu
import com.otus.designsystem.resources.symbol_tune
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.settings.presentation.FolderPillOption
import com.otus.mail.core.settings.presentation.InboxPills
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.SidebarPinOption
import com.otus.mail.core.settings.presentation.sectionSummary
import com.otus.mail.feature.mailsettings.presentation.inboxSummary
import com.otus.settings.presentation.components.ChoiceRowSpec
import com.otus.settings.presentation.components.PageRowSpec
import com.otus.settings.presentation.components.SettingRowSpec
import com.otus.settings.presentation.components.SettingsSectionSpec
import com.otus.settings.presentation.components.ToggleRowSpec

/**
 * Settings › Mail behavior: how Mail acts, in the order the user meets it. Inbox holds what the
 * Inbox lists and its pills, Panels the start page, the sidebar, and the filter panel, then Actions,
 * Reading, and Writing. [folderPills] are the folder pills the accounts' folders can fill,
 * [startPages] the sidebar mailboxes Mail can open on, and [rtl] names the swipes by their physical
 * direction. Each change is saved at once.
 */
fun mailBehaviorSettings(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
    startPages: List<SidebarPinOption>,
    rtl: Boolean,
    pages: BehaviorPages,
): List<SettingsSectionSpec> =
    listOf(
        SettingsSectionSpec("Inbox", inboxRows(state, folderPills)),
        SettingsSectionSpec("Panels", panelRows(state, startPages, pages)),
        SettingsSectionSpec("Actions", actionRows(state, rtl, pages.actionBar)),
        SettingsSectionSpec("Reading", readingRows(state)),
        SettingsSectionSpec("Writing", writingRows(state, pages.signatures)),
    )

/** What the Inbox lists and the inbox pills, each in a sheet, and Read later. */
private fun inboxRows(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
): List<SettingRowSpec> {
    val behavior = state.behavior
    return listOf(
        ChoiceRowSpec(
            "Inbox shows",
            inboxSummary(behavior.list.inbox),
            { InboxContentsSheet(state, it) },
            icon = Res.drawable.symbol_inbox,
            keywords = listOf(MailboxRole.Archive.title, MailboxRole.Junk.title, MailboxRole.Trash.title, "All mail"),
        ),
        ChoiceRowSpec(
            "Pills",
            shownCount(InboxPills(behavior.list.pills, folderPills).shown.size),
            { PillsSheet(state, folderPills, it) },
            icon = Res.drawable.symbol_filter_list,
            description = "Filters and folders above your mail",
            keywords = listOf("Chips", "Tabs"),
        ),
        ToggleRowSpec(
            "Read later",
            behavior.readLater,
            { on -> state.update { it.withReadLater(on) } },
            Res.drawable.symbol_bookmark,
            description = "One list for mail you save from every account",
            keywords = listOf("Save for later"),
        ),
    )
}

@Composable
private fun PillsSheet(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
    onDismiss: () -> Unit,
) {
    InboxPillsSheet(
        state.behavior.list.pills,
        folderPills,
        onApply = { pills -> state.updateList { it.copy(pills = pills) } },
        onDismiss = onDismiss,
    )
}

/** The page Mail opens on among [startPages], and the sidebar's and filter panel's pages. */
private fun panelRows(
    state: MailBehaviorState,
    startPages: List<SidebarPinOption>,
    pages: BehaviorPages,
): List<SettingRowSpec> {
    val behavior = state.behavior
    return listOf(
        startPageRow(state, startPages),
        PageRowSpec(
            "Sidebar",
            pages.sidebar,
            value = behavior.sidebar.layout.sectionSummary,
            icon = Res.drawable.symbol_menu,
            keywords = listOf("Navigation", "Drawer"),
        ) { sidebarSettings(state, startPages, pages.pinned) },
        PageRowSpec(
            "Filter panel",
            pages.filterPanel,
            value = filterPanelSummary(behavior.list.filters.sections.size),
            icon = Res.drawable.symbol_tune,
            keywords = listOf("Control panel", "Filters"),
        ) { filterPanelSettings(state) },
    )
}

/** The Filter panel row's summary: how many sections the panel shows. */
private fun filterPanelSummary(count: Int): String =
    when (count) {
        0 -> "No sections"
        1 -> "1 section"
        else -> "$count sections"
    }
