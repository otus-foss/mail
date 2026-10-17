package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.SettingToggleRow
import com.otus.designsystem.components.SheetFrame
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_report
import com.otus.mail.feature.inbox.domain.InboxContents
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/**
 * Chooses whether Spam and Trash join the Inbox page. Each switch is saved at once, and Done closes
 * the sheet. Whose mail the Inbox shows is the sidebar's "Show mail from" choice, which every mail
 * page shares.
 */
@Composable
internal fun InboxContentsSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    val contents = state.behavior.inbox
    val change: ((InboxContents) -> InboxContents) -> Unit = { update ->
        state.update { it.copy(inbox = update(it.inbox)) }
    }
    SheetFrame("Inbox shows", onDismiss) {
        SettingToggleRow(
            "Spam",
            checked = contents.spam,
            onCheckedChange = { on -> change { it.copy(spam = on) } },
            description = "Mail the server marked as spam",
            icon = Res.drawable.symbol_report,
        )
        SettingToggleRow(
            "Trash",
            checked = contents.trash,
            onCheckedChange = { on -> change { it.copy(trash = on) } },
            description = "Mail you deleted",
            icon = Res.drawable.symbol_delete,
        )
        PageNote("Mail stays in its own folders. This changes what the Inbox lists and counts.")
    }
}
