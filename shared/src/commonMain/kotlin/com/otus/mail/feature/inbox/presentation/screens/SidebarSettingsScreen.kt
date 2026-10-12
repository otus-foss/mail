package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceRow
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.ReorderableColumn
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SettingToggleRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.ValueButton
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_inbox
import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.SidebarCount
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.domain.SidebarSection
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.label
import com.otus.mail.feature.inbox.presentation.title

/**
 * Arranges the sidebar for every account. Drag a section's handle to move it, or switch it off to
 * hide it; the layout rows choose how mailboxes show and which counts appear. Each change is saved
 * at once. [mailboxes] names the built-in mailboxes.
 */
@Composable
fun SidebarSettingsScreen(
    state: MailBehaviorState,
    mailboxes: List<String>,
    onBack: () -> Unit,
) {
    val layout = state.behavior.sidebar
    val change: (
        (SidebarLayout) -> SidebarLayout,
    ) -> Unit = { update -> state.update { it.copy(sidebar = update(it.sidebar)) } }
    var choosing by rememberSaveable { mutableStateOf<LayoutChoice?>(null) }
    Subpage("Sidebar", onBack) {
        SettingsSection("Sections") {
            ReorderableColumn(
                layout.sections,
                key = { it.storageValue },
                onMove = { from, to -> change { it.moved(from, to) } },
            ) { section ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DragHandle(section.title)
                    SettingToggleRow(
                        section.title,
                        checked = section !in layout.hidden,
                        onCheckedChange = { shown -> change { it.withShown(section, shown) } },
                        modifier = Modifier.weight(1f),
                        description = section.description(mailboxes),
                        icon = section.icon,
                    )
                }
            }
        }
        SettingsSection("Layout") {
            SettingRow("Mailboxes", { choosing = LayoutChoice.Mailboxes }, description = "The built-in mailboxes") {
                ValueButton(layout.mailboxes.label)
            }
            SettingRow("Counts", { choosing = LayoutChoice.Counts }, description = "Next to mailboxes and folders") {
                ValueButton(layout.counts.label)
            }
        }
        PageNote("Drag a section to reorder it. One section stays on. Changes apply to every account.")
    }
    choosing?.let { LayoutChoiceSheet(it, layout, change) { choosing = null } }
}

@Composable
private fun LayoutChoiceSheet(
    choice: LayoutChoice,
    layout: SidebarLayout,
    change: ((SidebarLayout) -> SidebarLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    when (choice) {
        LayoutChoice.Mailboxes -> {
            ChoiceSheet(
                title = "Mailboxes",
                choices =
                    ChoiceOptions(
                        MailboxStyle.entries,
                        selected = layout.mailboxes,
                        default = MailboxStyle.Chips,
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

private enum class LayoutChoice { Mailboxes, Counts }

private val SidebarSection.icon
    get() =
        when (this) {
            SidebarSection.Mailboxes -> Res.drawable.symbol_inbox
            SidebarSection.Folders -> Res.drawable.symbol_folder
        }

private fun SidebarSection.description(mailboxes: List<String>): String =
    when (this) {
        SidebarSection.Mailboxes -> mailboxes.joinToString(", ")
        SidebarSection.Folders -> "Shared by all accounts"
    }
