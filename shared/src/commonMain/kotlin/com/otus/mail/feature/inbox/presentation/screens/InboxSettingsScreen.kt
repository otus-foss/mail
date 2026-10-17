package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.SettingToggleRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_report
import com.otus.mail.feature.inbox.domain.AccountAddresses
import com.otus.mail.feature.inbox.domain.InboxContents
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.avatar

/**
 * Chooses what the Inbox page lists: every address's mail or only some addresses', and whether
 * Spam and Trash join it. Each change is saved at once. [accounts] lists each account's addresses.
 */
@Composable
fun InboxSettingsScreen(
    state: MailBehaviorState,
    accounts: List<AccountAddresses>,
    onBack: () -> Unit,
) {
    val contents = state.behavior.inbox.within(accounts.map { it.accountId })
    val all = accounts.flatMap { account -> account.addresses.map { MailScopeChoice.Address(account.accountId, it) } }
    val change: ((InboxContents) -> InboxContents) -> Unit = { update ->
        state.update { it.copy(inbox = update(it.inbox.within(accounts.map { account -> account.accountId }))) }
    }
    Subpage("Inbox", onBack) {
        SettingsSection("Mail from") {
            SettingToggleRow(
                "Every address",
                checked = contents.everyAddress,
                onCheckedChange = { every -> change { it.copy(addresses = if (every) emptyList() else all) } },
                description = "Turn off to choose addresses",
                icon = Res.drawable.symbol_inbox,
            )
        }
        if (!contents.everyAddress) {
            accounts.filter { it.addresses.isNotEmpty() }.forEach { account ->
                SettingsSection(account.name, account.avatar) {
                    account.addresses.forEach { address ->
                        val choice = MailScopeChoice.Address(account.accountId, address)
                        SettingToggleRow(
                            address,
                            checked = contents.has(choice),
                            onCheckedChange = { on -> change { it.with(choice, on, all) } },
                        )
                    }
                }
            }
        }
        SettingsSection("Also show") {
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
        }
        PageNote("Mail stays in its own folders. This changes what the Inbox lists and counts.")
    }
}
