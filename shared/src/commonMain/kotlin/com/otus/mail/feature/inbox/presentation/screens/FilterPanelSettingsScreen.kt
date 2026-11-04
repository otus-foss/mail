package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.components.FilterPanelSettings

/**
 * Settings › Mail behavior › Filter panel: what the panel above the mail list shows, and what its
 * quick view shows when pulled down. Each sheet saves on Done.
 */
@Composable
fun FilterPanelSettingsScreen(
    state: MailBehaviorState,
    onBack: () -> Unit,
) {
    Subpage("Filter panel", onBack) { FilterPanelSettings(state) }
}
