package com.otusfoss.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.pages.components.Subpage
import com.otusfoss.core.settings.presentation.components.SettingSections
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.feature.mailsettings.presentation.components.filterPanelSettings

/**
 * Settings › Mail behavior › Filter panel: what the panel above the mail list shows, and what its
 * quick view shows when pulled down. Each sheet saves on Done.
 */
@Composable
fun FilterPanelSettingsScreen(
    state: MailBehaviorState,
    onBack: () -> Unit,
) {
    Subpage("Filter panel", onBack) { SettingSections(filterPanelSettings(state)) }
}
