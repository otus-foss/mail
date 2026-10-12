package com.otus.mail.app

import androidx.compose.runtime.Composable
import com.otus.accounts.domain.ConnectedAccount
import com.otus.avatar.generatedAvatar
import com.otus.designsystem.components.SidebarRail
import com.otus.designsystem.elements.SidebarRailAccount
import com.otus.designsystem.elements.SidebarRailAdd
import com.otus.designsystem.elements.SidebarRailDivider
import com.otus.designsystem.elements.SidebarRailIcon
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_all_inbox
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.inbox.domain.SidebarCount

/**
 * All, then each account by its initials, each with its [inboxes]' [counts], then Add account and
 * Settings. Tapping All while it is chosen opens the inbox of every account, so a double tap
 * chooses All and opens that inbox.
 */
@Composable
internal fun MailSidebarRail(
    accounts: List<ConnectedAccount>,
    inboxes: List<MailFolder>,
    counts: SidebarCount,
    selected: String?,
    onSelectAccount: (String?) -> Unit,
    onSelect: (MailPage) -> Unit,
) {
    val counter = SidebarCounter(null, counts)
    SidebarRail(onSettings = { onSelect(MailPage.Settings) }) {
        SidebarRailIcon(
            Res.drawable.symbol_all_inbox,
            label = "All",
            description = "All accounts",
            selected = selected == null,
            onClick = { if (selected == null) onSelect(MailPage.Inbox) else onSelectAccount(null) },
            badge = counter.badge(inboxes),
        )
        SidebarRailDivider()
        accounts.forEach { account ->
            SidebarRailAccount(
                generatedAvatar(account.name, account.username).initials,
                description = account.name,
                selected = selected == account.id,
                onClick = { onSelectAccount(account.id) },
                badge = counter.badge(inboxes.filter { it.accountId == account.id }),
            )
        }
        SidebarRailAdd("Add account", onClick = { onSelect(MailPage.EmailAccounts) })
    }
}
