package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.SidebarPinOption
import com.otus.mail.feature.inbox.presentation.components.ActionSettings
import com.otus.mail.feature.inbox.presentation.components.InboxBehaviorSettings
import com.otus.mail.feature.inbox.presentation.components.PanelSettings
import com.otus.mail.feature.inbox.presentation.components.ReadingSettings
import com.otus.mail.feature.inbox.presentation.components.WritingSettings

/**
 * Settings › Mail behavior: how Mail acts, in the order the user meets it. Inbox holds what the
 * Inbox lists and its pills, Panels the start page, the sidebar, and the filter panel, then Actions, Reading, and
 * Writing. [open] opens one of the pages its rows lead to; [folderPills] are the folder pills the
 * accounts' folders can fill, and [startPages] the sidebar mailboxes Mail can open on. Each change
 * is saved at once.
 */
@Composable
fun MailBehaviorScreen(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
    startPages: List<SidebarPinOption>,
    open: (BehaviorPage) -> Unit,
    onBack: () -> Unit,
) {
    Subpage("Mail behavior", onBack) {
        InboxBehaviorSettings(state, folderPills)
        PanelSettings(
            state,
            startPages,
            onSidebar = { open(BehaviorPage.Sidebar) },
            onFilterPanel = { open(BehaviorPage.FilterPanel) },
        )
        ActionSettings(state) { open(BehaviorPage.ActionBar) }
        ReadingSettings(state)
        WritingSettings(state) { open(BehaviorPage.Signatures) }
    }
}

/** A page that a row on [MailBehaviorScreen] opens. */
enum class BehaviorPage { Sidebar, FilterPanel, ActionBar, Signatures }
