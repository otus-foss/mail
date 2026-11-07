package com.otus.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.mail.core.settings.domain.MailListLayout
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.mailsettings.presentation.components.mailListSettings
import com.otus.settings.presentation.components.SettingSections

/** How the mail list's rows read, with [preview] showing a row in the current layout at the top. */
@Composable
fun MailListSettingsScreen(
    state: MailBehaviorState,
    dateFormatter: DateFormatter,
    preview: @Composable (MailListLayout) -> Unit,
    onBack: () -> Unit,
) {
    Subpage("Mail list", onBack) {
        preview(state.behavior.list.layout)
        SettingSections(mailListSettings(state, dateFormatter))
    }
}
