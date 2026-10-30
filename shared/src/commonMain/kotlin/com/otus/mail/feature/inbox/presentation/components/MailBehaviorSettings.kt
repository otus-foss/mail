package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_format_list_bulleted
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.resources.symbol_menu
import com.otus.designsystem.resources.symbol_swipe_left
import com.otus.designsystem.resources.symbol_swipe_right
import com.otus.designsystem.resources.symbol_view_agenda
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.settings.components.SettingPageRow
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.inboxSummary
import com.otus.mail.feature.inbox.presentation.summary
import org.jetbrains.compose.resources.DrawableResource

/**
 * Mail's rows in the appearance page's Look section: the sidebar's page, opened with [onSidebar],
 * and the mail list's, opened with [onMailList].
 */
@Composable
fun MailLookSettings(
    behavior: MailBehavior,
    onSidebar: () -> Unit,
    onMailList: () -> Unit,
) {
    val sections = behavior.sidebar.shown.size
    SettingPageRow(
        "Sidebar",
        onSidebar,
        description = if (sections == 1) "1 section" else "$sections sections",
        icon = Res.drawable.symbol_menu,
    )
    SettingPageRow(
        "Mail list",
        onMailList,
        description = behavior.list.summary,
        icon = Res.drawable.symbol_format_list_bulleted,
    )
}

/**
 * The Mail behavior page's Inbox section: what the Inbox lists, the inbox pills, the filter
 * panel's sections and choices, and Read later, all but the last in sheets. [folderPills] are the
 * folder pills the accounts' folders can fill.
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
        FilterPanelSettings(state)
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
 * Swipe, action bar, reading, and sending choices for the Mail behavior page.
 * [onOpenActionBar] opens the action bar's page. Each swipe opens a choice of actions; the rows
 * name the physical direction for the current layout direction.
 */
@Composable
fun MailBehaviorSettings(
    state: MailBehaviorState,
    onOpenActionBar: () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var choosing by rememberSaveable { mutableStateOf<SwipeSide?>(null) }
    val behavior = state.behavior
    SettingsSection("Actions") {
        SwipeSide.entries.forEach { side ->
            SettingRow(side.title(rtl), { choosing = side }, icon = side.icon(rtl)) {
                ValueButton(side.of(behavior).swipeLabel)
            }
        }
        SettingPageRow("Action bar", onOpenActionBar, icon = Res.drawable.symbol_view_agenda)
    }
    SettingsSection("Reading") {
        SettingToggleRow(
            "Mark as read when opened",
            checked = behavior.markReadOnOpen,
            onCheckedChange = { on -> state.update { it.copy(markReadOnOpen = on) } },
            icon = Res.drawable.symbol_mark_email_read,
        )
        SettingToggleRow(
            "Show quoted text",
            checked = behavior.showQuotes,
            onCheckedChange = { on -> state.update { it.copy(showQuotes = on) } },
            description = "When off, replies show only what's new",
            info =
                InfoText(
                    "Show quoted text",
                    "With it off, swipe through the conversation to read the earlier messages.",
                ),
            icon = Res.drawable.symbol_forum,
        )
    }
    SendingSettings(state)
    val side = choosing ?: return
    SwipeChoice(state, side, side.title(rtl)) { choosing = null }
}

@Composable
private fun SwipeChoice(
    state: MailBehaviorState,
    side: SwipeSide,
    title: String,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        title = title,
        choices =
            ChoiceOptions(
                listOf(null) + state.behavior.swipeChoices,
                selected = side.of(state.behavior),
                default = side.default,
            ),
        row = { swipe -> ChoiceRow(swipe.swipeLabel, "Default".takeIf { swipe == side.default }) },
        onSelect = { swipe -> state.update { side.with(it, swipe) } },
        onDismiss = onDismiss,
    )
}

private enum class SwipeSide {
    StartToEnd,
    EndToStart,
    ;

    fun title(rtl: Boolean): String = if ((this == StartToEnd) != rtl) "Swipe right" else "Swipe left"

    fun icon(rtl: Boolean): DrawableResource =
        if ((this == StartToEnd) != rtl) Res.drawable.symbol_swipe_right else Res.drawable.symbol_swipe_left

    /** What this swipe does until the user changes it. */
    val default: MailAction?
        get() = of(MailBehavior())

    fun of(behavior: MailBehavior): MailAction? =
        when (this) {
            StartToEnd -> behavior.startToEnd
            EndToStart -> behavior.endToStart
        }

    fun with(
        behavior: MailBehavior,
        swipe: MailAction?,
    ): MailBehavior =
        when (this) {
            StartToEnd -> behavior.copy(startToEnd = swipe)
            EndToStart -> behavior.copy(endToStart = swipe)
        }
}

/** A swipe's name in settings; no action is "Nothing". */
private val MailAction?.swipeLabel: String
    get() = this?.label ?: "Nothing"
