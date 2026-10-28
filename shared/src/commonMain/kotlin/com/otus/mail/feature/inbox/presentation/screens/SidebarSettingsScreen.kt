package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.designsystem.shared.reorder.components.ReorderableColumn
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.SidebarCount
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.domain.SidebarPin
import com.otus.mail.feature.inbox.domain.SidebarSection
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.SidebarPinOption
import com.otus.mail.feature.inbox.presentation.components.OrderedChoice
import com.otus.mail.feature.inbox.presentation.components.OrderedChoiceSheet
import com.otus.mail.feature.inbox.presentation.label
import com.otus.mail.feature.inbox.presentation.title

/**
 * Arranges the sidebar for every account. Drag a section's handle to move it, or switch it off to
 * hide it. The Pinned rows choose what shows below the Inbox, from [pins], and how; the layout rows
 * choose which counts appear, and a switch hides folder colors without forgetting them. Each change
 * is saved at once.
 */
@Composable
fun SidebarSettingsScreen(
    state: MailBehaviorState,
    pins: List<SidebarPinOption>,
    onBack: () -> Unit,
) {
    val layout = state.behavior.sidebar
    val change: (
        (SidebarLayout) -> SidebarLayout,
    ) -> Unit = { update -> state.update { it.copy(sidebar = update(it.sidebar)) } }
    var choosing by rememberSaveable { mutableStateOf<LayoutChoice?>(null) }
    val pinned = layout.pinned.mapNotNull { pin -> pins.firstOrNull { it.pin == pin } }
    Subpage("Sidebar", onBack) {
        SettingsSection("Sections") {
            ReorderableColumn(
                layout.sections,
                key = { it.storageValue },
                onMove = { from, to -> change { it.moved(from, to) } },
            ) { section ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingToggleRow(
                        section.title,
                        checked = section !in layout.hidden,
                        onCheckedChange = { shown -> change { it.withShown(section, shown) } },
                        modifier = Modifier.weight(1f),
                        icon = section.icon,
                    )
                    DragHandle(section.title)
                }
            }
        }
        SettingsSection("Pinned") {
            SettingRow(
                "Mailboxes and folders",
                { choosing = LayoutChoice.Pinned },
                description = "Shown below the Inbox",
            ) {
                ValueButton(pinned.size.toString())
            }
            SettingRow("Show as", { choosing = LayoutChoice.Mailboxes }) {
                ValueButton(layout.mailboxes.label)
            }
        }
        SettingsSection("Layout") {
            SettingRow("Counts", { choosing = LayoutChoice.Counts }, description = "Next to mailboxes and folders") {
                ValueButton(layout.counts.label)
            }
            SettingToggleRow(
                "Folder colors",
                checked = layout.folderColors,
                onCheckedChange = { shown -> change { it.copy(folderColors = shown) } },
                description = "Only changes the folder icon",
                info = InfoText("Folder colors", "Colors stay on this device. Other apps and devices don't see them."),
            )
        }
    }
    choosing?.let { LayoutChoiceSheet(it, layout, pins, change) { choosing = null } }
}

@Composable
private fun LayoutChoiceSheet(
    choice: LayoutChoice,
    layout: SidebarLayout,
    pins: List<SidebarPinOption>,
    change: ((SidebarLayout) -> SidebarLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    when (choice) {
        LayoutChoice.Pinned -> {
            OrderedChoiceSheet(
                "Pinned",
                layout.pinned,
                pins.map { OrderedChoice(it.pin, it.pin.storageValue, it.path, it.icon, it.group) },
                keyOf = SidebarPin::storageValue,
                onApply = { pinned -> change { it.copy(pinned = pinned) } },
                onDismiss = onDismiss,
            )
        }

        LayoutChoice.Mailboxes -> {
            ChoiceSheet(
                title = "Show pinned as",
                choices =
                    ChoiceOptions(
                        MailboxStyle.entries,
                        selected = layout.mailboxes,
                        default = MailboxStyle.Grid,
                    ),
                row = { ChoiceRow(it.label) },
                onSelect = { style -> change { it.copy(mailboxes = style) } },
                onDismiss = onDismiss,
            )
        }

        LayoutChoice.Counts -> {
            ChoiceSheet(
                title = "Counts",
                choices = ChoiceOptions(SidebarCount.entries, selected = layout.counts, default = SidebarCount.Unread),
                row = { ChoiceRow(it.label) },
                onSelect = { count -> change { it.copy(counts = count) } },
                onDismiss = onDismiss,
            )
        }
    }
}

private enum class LayoutChoice { Pinned, Mailboxes, Counts }

private val SidebarSection.icon
    get() =
        when (this) {
            SidebarSection.Mailboxes -> Res.drawable.symbol_inbox
            SidebarSection.Folders -> Res.drawable.symbol_folder
        }
