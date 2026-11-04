package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_format_list_bulleted
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_menu
import com.otus.designsystem.resources.symbol_tune
import com.otus.designsystem.shared.settings.components.SettingPageRow
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.inboxSummary
import com.otus.mail.feature.inbox.presentation.sectionSummary
import com.otus.mail.feature.inbox.presentation.summary

/** Mail's row in the Appearance page's Look section: the mail list's page, opened with [onMailList]. */
@Composable
fun MailLookSettings(
    behavior: MailBehavior,
    onMailList: () -> Unit,
) {
    SettingPageRow(
        "Mail list",
        onMailList,
        description = behavior.list.summary,
        icon = Res.drawable.symbol_format_list_bulleted,
    )
}

/**
 * The Mail behavior page's Inbox section: what the Inbox lists and the inbox pills, each in a
 * sheet, and Read later. [folderPills] are the folder pills the accounts' folders can fill.
 */
@Composable
fun InboxBehaviorSettings(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
) {
    var choosingInbox by rememberSaveable { mutableStateOf(false) }
    var choosingPills by rememberSaveable { mutableStateOf(false) }
    val behavior = state.behavior
    SettingsSection("Inbox") {
        SettingRow("Inbox shows", { choosingInbox = true }, icon = Res.drawable.symbol_inbox) {
            ValueButton(inboxSummary(behavior.inbox))
        }
        val shown = InboxPills(behavior.pills, folderPills).shown.size
        SettingRow(
            "Pills",
            { choosingPills = true },
            description = "Filters and folders above your mail",
            icon = Res.drawable.symbol_filter_list,
        ) { ValueButton(shownCount(shown)) }
        SettingToggleRow(
            "Read later",
            checked = behavior.readLater,
            onCheckedChange = { on -> state.update { it.withReadLater(on) } },
            description = "One list for mail you save from every account",
            icon = Res.drawable.symbol_bookmark,
        )
    }
    if (choosingInbox) InboxContentsSheet(state) { choosingInbox = false }
    if (choosingPills) {
        InboxPillsSheet(
            behavior.pills,
            folderPills,
            onApply = { pills -> state.update { it.copy(pills = pills) } },
            onDismiss = { choosingPills = false },
        )
    }
}

/**
 * The Mail behavior page's Panels section: the sidebar's page, opened with [onSidebar], and the
 * filter panel's, opened with [onFilterPanel]. Each names what its panel shows.
 */
@Composable
fun PanelSettings(
    behavior: MailBehavior,
    onSidebar: () -> Unit,
    onFilterPanel: () -> Unit,
) {
    SettingsSection("Panels") {
        SettingPageRow(
            "Sidebar",
            onSidebar,
            description = behavior.sidebar.sectionSummary,
            icon = Res.drawable.symbol_menu,
        )
        SettingPageRow(
            "Filter panel",
            onFilterPanel,
            description = filterPanelSummary(behavior.filters.sections.size),
            icon = Res.drawable.symbol_tune,
        )
    }
}

/** The Filter panel row's summary: how many sections the panel shows. */
internal fun filterPanelSummary(count: Int): String =
    when (count) {
        0 -> "No sections"
        1 -> "1 section"
        else -> "$count sections"
    }
