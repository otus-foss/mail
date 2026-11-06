package com.otus.mail.feature.mailsettings.presentation.components

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.settings.presentation.components.SettingRowSpec
import com.otus.settings.presentation.components.ToggleRowSpec

/** The Mail behavior page's Reading rows: marking mail read, quoted text, and the Read view's quick view. */
internal fun readingRows(state: MailBehaviorState): List<SettingRowSpec> {
    val read = state.behavior.read
    return listOf(
        ToggleRowSpec(
            "Mark as read when opened",
            read.markReadOnOpen,
            { on -> state.updateRead { it.copy(markReadOnOpen = on) } },
            Res.drawable.symbol_mark_email_read,
        ),
        ToggleRowSpec(
            "Show quoted text",
            read.showQuotes,
            { on -> state.updateRead { it.copy(showQuotes = on) } },
            Res.drawable.symbol_forum,
            info = InfoText("Show quoted text", "Swipe through the conversation to read earlier messages."),
        ),
        readQuickViewRow(state),
    )
}
