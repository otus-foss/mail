package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.Subpage
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.components.InboxBehaviorSettings
import com.otus.mail.feature.inbox.presentation.components.MailBehaviorSettings

/**
 * Settings › Mail behavior: what the Inbox lists, swipes and the action bar, reading, and sending.
 * [onOpenActionBar] opens the action bar's page; [folderPills] are the folder pills the
 * accounts' folders can fill. Each change is saved at once.
 */
@Composable
fun MailBehaviorScreen(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
    onOpenActionBar: () -> Unit,
    onBack: () -> Unit,
) {
    Subpage("Mail behavior", onBack) {
        InboxBehaviorSettings(state, folderPills)
        MailBehaviorSettings(state, onOpenActionBar)
    }
}
