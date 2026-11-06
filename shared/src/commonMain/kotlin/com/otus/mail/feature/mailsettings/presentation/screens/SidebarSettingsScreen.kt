package com.otus.mail.feature.mailsettings.presentation.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.designsystem.shared.reorder.components.ReorderableColumn
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.SidebarPinOption
import com.otus.mail.feature.mailsettings.presentation.components.changeSidebar
import com.otus.mail.feature.mailsettings.presentation.components.sidebarLayoutSettings
import com.otus.mail.feature.mailsettings.presentation.components.sidebarSectionToggles
import com.otus.settings.presentation.components.SettingSections
import com.otus.settings.presentation.components.SettingSpecRow

/**
 * Arranges the sidebar for every account. Drag a section's handle to move it, or switch it off to
 * hide it. Below, [sidebarLayoutSettings] lead to the Pinned page, which [onPinned] opens to
 * arrange the mailboxes from [pins]. Each change is saved at once.
 */
@Composable
fun SidebarSettingsScreen(
    state: MailBehaviorState,
    pins: List<SidebarPinOption>,
    onPinned: () -> Unit,
    onBack: () -> Unit,
) {
    Subpage("Sidebar", onBack) {
        SettingsSection("Sections") {
            ReorderableColumn(
                sidebarSectionToggles(state),
                key = { it.title },
                onMove = { from, to -> state.changeSidebar { it.moved(from, to) } },
            ) { toggle ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingSpecRow(toggle, Modifier.weight(1f))
                    DragHandle(toggle.title)
                }
            }
        }
        SettingSections(sidebarLayoutSettings(state, pins) { onPinned() })
    }
}
