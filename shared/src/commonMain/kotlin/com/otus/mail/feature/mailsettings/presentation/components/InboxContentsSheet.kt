package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_archive
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_report
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.pages.components.PageNote
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.mail.core.settings.domain.InboxContents
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.icon

/**
 * Chooses which folders join the Inbox page: Archive and the user's own folders keep filed mail
 * there, and Spam and Trash add mail the user would otherwise not see. Each switch is saved at once, and Done closes
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
            "Archive",
            checked = contents.archive,
            onCheckedChange = { on -> change { it.copy(archive = on) } },
            icon = Res.drawable.symbol_archive,
        )
        SettingToggleRow(
            "Your folders",
            checked = contents.ownFolders,
            onCheckedChange = { on -> change { it.copy(ownFolders = on) } },
            icon = Res.drawable.symbol_folder,
        )
        SettingToggleRow(
            "Spam",
            checked = contents.spam,
            onCheckedChange = { on -> change { it.copy(spam = on) } },
            icon = Res.drawable.symbol_report,
        )
        SettingToggleRow(
            "Trash",
            checked = contents.trash,
            onCheckedChange = { on -> change { it.copy(trash = on) } },
            icon = Res.drawable.symbol_delete,
        )
        PageNote("Mail stays in its own folders.")
    }
}
