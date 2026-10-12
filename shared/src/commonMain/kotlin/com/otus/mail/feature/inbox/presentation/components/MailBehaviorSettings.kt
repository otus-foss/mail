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
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.elements.ValueButton
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_chevron_right
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/**
 * Inbox, swipe, and reading choices for the appearance page. [folderPills] are the folder pills
 * the accounts' folders can fill, and [onSidebar] opens the sidebar's page. [onOpenActionBar]
 * opens the action bar's page. Each swipe opens a choice of actions; the rows name the physical
 * direction for the current layout direction.
 */
@Composable
fun MailBehaviorSettings(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
    onSidebar: () -> Unit,
    onOpenActionBar: () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var choosing by rememberSaveable { mutableStateOf<SwipeSide?>(null) }
    var choosingPills by rememberSaveable { mutableStateOf(false) }
    val behavior = state.behavior
    SettingsSection("Inbox") {
        val shown = InboxPills(behavior.pills, folderPills).shown.size
        SettingRow("Pills", { choosingPills = true }, description = "Filters and folders above your mail") {
            ValueButton(if (shown == 0) "None" else "$shown shown")
        }
    }
    SettingsSection("Sidebar") {
        SettingRow("Sidebar", onSidebar, description = "Sections, mailboxes, and counts") {
            Symbol(Res.drawable.symbol_chevron_right, mirrorInRtl = true)
        }
    }
    SettingsSection("Action bar") {
        SettingRow("Action bar", onOpenActionBar, description = "Buttons at the bottom of each screen") {
            Symbol(Res.drawable.symbol_chevron_right, mirrorInRtl = true)
        }
        SettingToggleRow(
            "Minimize",
            checked = behavior.minimizedBar,
            onCheckedChange = { on -> state.update { it.copy(minimizedBar = on) } },
            description = "One round button for the main action",
        )
    }
    SettingsSection("Actions") {
        SwipeSide.entries.forEach { side ->
            SettingRow(side.title(rtl), { choosing = side }, description = side.description(rtl)) {
                ValueButton(side.of(behavior).swipeLabel)
            }
        }
    }
    SettingsSection("Reading") {
        SettingToggleRow(
            "Mark as read when opened",
            checked = behavior.markReadOnOpen,
            onCheckedChange = { on -> state.update { it.copy(markReadOnOpen = on) } },
            description = "Otherwise mail stays unread until you mark it",
        )
        SettingToggleRow(
            "Read later",
            checked = behavior.readLater,
            onCheckedChange = { on -> state.update { it.withReadLater(on) } },
            description = "Adds a Read later folder, pill, and button",
        )
    }
    if (choosingPills) {
        InboxPillsSheet(
            behavior.pills,
            folderPills,
            onApply = { pills -> state.update { it.copy(pills = pills) } },
            onDismiss = { choosingPills = false },
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

    fun description(rtl: Boolean): String =
        if ((this == StartToEnd) != rtl) "Drag a message to the right" else "Drag a message to the left"

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
