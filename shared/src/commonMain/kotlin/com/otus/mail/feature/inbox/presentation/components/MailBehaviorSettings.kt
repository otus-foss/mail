package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SettingToggleRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.elements.ValueButton
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MessageSwipe
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/**
 * Inbox, swipe, and reading choices for the appearance page. [folderPills] are the folder pills
 * the accounts' folders can fill. Each swipe opens a choice of actions; the rows name the physical
 * direction for the current layout direction.
 */
@Composable
fun MailBehaviorSettings(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
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
    SettingsSection("Swipe actions") {
        SwipeSide.entries.forEach { side ->
            SettingRow(side.title(rtl), { choosing = side }, description = side.description(rtl)) {
                ValueButton(side.of(behavior).label)
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
    SettingsSection("Folders") {
        SettingToggleRow(
            "Combined folders",
            checked = behavior.combinedFolders,
            onCheckedChange = { on -> state.update { it.copy(combinedFolders = on) } },
            description = "Shows one Drafts, Sent, Archive, Spam, and Trash for all accounts",
        )
    }
    choosing?.let { side ->
        ChoiceSheet(
            title = side.title(rtl),
            choices = ChoiceOptions(behavior.swipeChoices, selected = side.of(behavior), default = side.default),
            label = { it.label },
            detail = { swipe -> "Default".takeIf { swipe == side.default } },
            onSelect = { swipe -> state.update { side.with(it, swipe) } },
            onDismiss = { choosing = null },
        )
    }
}

private enum class SwipeSide {
    StartToEnd,
    EndToStart,
    ;

    fun title(rtl: Boolean): String = if ((this == StartToEnd) != rtl) "Swipe right" else "Swipe left"

    fun description(rtl: Boolean): String =
        if ((this == StartToEnd) != rtl) "Drag a message to the right" else "Drag a message to the left"

    /** What this swipe does until the user changes it. */
    val default: MessageSwipe
        get() = of(MailBehavior())

    fun of(behavior: MailBehavior): MessageSwipe =
        when (this) {
            StartToEnd -> behavior.startToEnd
            EndToStart -> behavior.endToStart
        }

    fun with(
        behavior: MailBehavior,
        swipe: MessageSwipe,
    ): MailBehavior =
        when (this) {
            StartToEnd -> behavior.copy(startToEnd = swipe)
            EndToStart -> behavior.copy(endToStart = swipe)
        }
}
