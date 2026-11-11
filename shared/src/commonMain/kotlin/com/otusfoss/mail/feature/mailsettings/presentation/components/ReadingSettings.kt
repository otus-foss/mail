package com.otusfoss.mail.feature.mailsettings.presentation.components

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_forum
import com.otusfoss.core.designsystem.resources.symbol_mark_email_read
import com.otusfoss.core.designsystem.shared.feedback.components.InfoText
import com.otusfoss.core.settings.presentation.components.SettingRowSpec
import com.otusfoss.core.settings.presentation.components.ToggleRowSpec
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState

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
    )
}
