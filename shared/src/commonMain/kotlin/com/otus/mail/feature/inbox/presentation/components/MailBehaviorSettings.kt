package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceRow
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SettingToggleRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.elements.ValueButton
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_collapse_content
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.resources.symbol_menu
import com.otus.designsystem.resources.symbol_swipe_left
import com.otus.designsystem.resources.symbol_swipe_right
import com.otus.designsystem.resources.symbol_view_agenda
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.jetbrains.compose.resources.DrawableResource

/**
 * Mail's rows in the appearance page's Look section: the sidebar's page, opened with [onSidebar],
 * and the inbox pills. [folderPills] are the folder pills the accounts' folders can fill.
 */
@Composable
fun MailLookSettings(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
    onSidebar: () -> Unit,
) {
    var choosingPills by rememberSaveable { mutableStateOf(false) }
    val behavior = state.behavior
    SettingRow("Sidebar", onSidebar, icon = Res.drawable.symbol_menu) { ValueButton("Edit") }
    val shown = InboxPills(behavior.pills, folderPills).shown.size
    SettingRow(
        "Pills",
        { choosingPills = true },
        description = "Filters and folders above your mail",
        icon = Res.drawable.symbol_filter_list,
    ) { ValueButton(if (shown == 0) "None" else "$shown shown") }
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
 * Swipe, action bar, and reading choices for the appearance page. [onOpenActionBar] opens the
 * action bar's page. Each swipe opens a choice of actions; the rows name the physical direction
 * for the current layout direction.
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
        SettingRow("Action bar", onOpenActionBar, icon = Res.drawable.symbol_view_agenda) {
            ValueButton(behavior.actionBarMode.label)
        }
        SettingToggleRow(
            "Minimize action bar",
            checked = behavior.minimizedBar,
            onCheckedChange = { on -> state.update { it.copy(minimizedBar = on) } },
            description = "One round button for the main action",
            icon = Res.drawable.symbol_collapse_content,
        )
    }
    SettingsSection("Reading") {
        SettingToggleRow(
            "Mark as read when opened",
            checked = behavior.markReadOnOpen,
            onCheckedChange = { on -> state.update { it.copy(markReadOnOpen = on) } },
            icon = Res.drawable.symbol_mark_email_read,
        )
        SettingToggleRow(
            "Read later",
            checked = behavior.readLater,
            onCheckedChange = { on -> state.update { it.withReadLater(on) } },
            icon = Res.drawable.symbol_bookmark,
        )
    }
    choosing?.let { side -> SwipeChoice(state, side, side.title(rtl)) { choosing = null } }
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
