package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.mail.core.settings.presentation.MailBehaviorState

/** The Mail behavior page's Reading section: marking mail read, quoted text, and the Read view's quick view. */
@Composable
fun ReadingSettings(state: MailBehaviorState) {
    val behavior = state.behavior
    SettingsSection("Reading") {
        SettingToggleRow(
            "Mark as read when opened",
            checked = behavior.markReadOnOpen,
            onCheckedChange = { on -> state.update { it.copy(markReadOnOpen = on) } },
            icon = Res.drawable.symbol_mark_email_read,
        )
        SettingToggleRow(
            "Show quoted text",
            checked = behavior.showQuotes,
            onCheckedChange = { on -> state.update { it.copy(showQuotes = on) } },
            info = InfoText("Show quoted text", "Swipe through the conversation to read earlier messages."),
            icon = Res.drawable.symbol_forum,
        )
        ReadQuickViewSetting(state)
    }
}
