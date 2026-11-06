package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.designsystem.shared.reorder.components.ReorderableColumn
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.settings.components.DependentSettings
import com.otus.designsystem.shared.settings.components.SettingPageRow
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.core.settings.domain.SidebarCount
import com.otus.mail.core.settings.domain.SidebarLayout
import com.otus.mail.core.settings.domain.SidebarSection
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.PINNED_UNAVAILABLE
import com.otus.mail.core.settings.presentation.SidebarPinOption
import com.otus.mail.core.settings.presentation.icon
import com.otus.mail.core.settings.presentation.label
import com.otus.mail.core.settings.presentation.pinnedSummary
import com.otus.mail.core.settings.presentation.title

/**
 * Arranges the sidebar for every account. Drag a section's handle to move it, or switch it off to
 * hide it. The Pinned row opens [onPinned], the page that arranges the mailboxes from [pins] and
 * chooses how they show; it waits while the Pinned section is off, since nothing would show them.
 * The layout rows choose which counts appear, and a switch hides folder colors without forgetting
 * them. Each change is saved at once.
 */
@Composable
fun SidebarSettingsScreen(
    state: MailBehaviorState,
    pins: List<SidebarPinOption>,
    onPinned: () -> Unit,
    onBack: () -> Unit,
) {
    val layout = state.behavior.sidebar
    val change: ((SidebarLayout) -> SidebarLayout) -> Unit = { update ->
        state.update { it.copy(sidebar = update(it.sidebar)) }
    }
    var choosingCounts by rememberSaveable { mutableStateOf(false) }
    val pinned = layout.pinned.mapNotNull { pin -> pins.firstOrNull { it.pin == pin } }
    val pinnedShown = SidebarSection.Mailboxes !in layout.hidden
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
            DependentSettings(enabled = pinnedShown) {
                SettingPageRow(
                    "Mailboxes and folders",
                    onPinned,
                    description = if (pinnedShown) pinnedSummary(layout.mailboxes, pinned.size) else PINNED_UNAVAILABLE,
                )
            }
        }
        SettingsSection("Layout") {
            SettingRow("Counts", { choosingCounts = true }) {
                ValueButton(layout.counts.label)
            }
            SettingToggleRow(
                "Folder colors",
                checked = layout.folderColors,
                onCheckedChange = { shown -> change { it.copy(folderColors = shown) } },
                info = InfoText("Folder colors", "Only on this device."),
            )
        }
    }
    if (choosingCounts) {
        ChoiceSheet(
            title = "Counts",
            choices = ChoiceOptions(SidebarCount.entries, selected = layout.counts, default = SidebarCount.Unread),
            row = { ChoiceRow(it.label) },
            onSelect = { count -> change { it.copy(counts = count) } },
            onDismiss = { choosingCounts = false },
        )
    }
}
