package com.otusfoss.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.designsystem.shared.pages.components.Subpage
import com.otusfoss.core.settings.presentation.components.SettingSections
import com.otusfoss.mail.core.settings.domain.MailListLayout
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.feature.mailsettings.presentation.components.mailListSettings

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
