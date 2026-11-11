package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_folder
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.pages.components.PageNote
import com.otusfoss.core.designsystem.shared.settings.components.SettingToggleRow
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.presentation.symbol
import com.otusfoss.mail.core.settings.domain.InboxContents
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.icon

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
    val contents = state.behavior.list.inbox
    val change: ((InboxContents) -> InboxContents) -> Unit = { update ->
        state.updateList { it.copy(inbox = update(it.inbox)) }
    }
    SheetFrame("Inbox shows", onDismiss) {
        SettingToggleRow(
            MailboxRole.Archive.title,
            checked = contents.archive,
            onCheckedChange = { on -> change { it.copy(archive = on) } },
            icon = MailboxRole.Archive.symbol,
        )
        SettingToggleRow(
            "Your folders",
            checked = contents.ownFolders,
            onCheckedChange = { on -> change { it.copy(ownFolders = on) } },
            icon = Res.drawable.symbol_folder,
        )
        SettingToggleRow(
            MailboxRole.Junk.title,
            checked = contents.spam,
            onCheckedChange = { on -> change { it.copy(spam = on) } },
            icon = MailboxRole.Junk.symbol,
        )
        SettingToggleRow(
            MailboxRole.Trash.title,
            checked = contents.trash,
            onCheckedChange = { on -> change { it.copy(trash = on) } },
            icon = MailboxRole.Trash.symbol,
        )
        PageNote("Mail stays in its own folders.")
    }
}
