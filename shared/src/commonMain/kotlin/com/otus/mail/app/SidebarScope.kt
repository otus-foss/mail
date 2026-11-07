package com.otus.mail.app

import androidx.compose.runtime.Composable
import com.otus.accounts.domain.ownAddresses
import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.messages.domain.AccountAddresses
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.messages.presentation.components.MailScopeSheet
import com.otus.mail.core.messages.presentation.components.MailScopeSwitch
import com.otus.mail.feature.sidebar.presentation.scopeCount

/** Each account's sign-in and sending addresses, for "Show mail from". */
internal fun MailStates.accountAddresses(): List<AccountAddresses> =
    accounts.accounts.map { account ->
        val addresses = AccountAddresses(account.id, account.name, account.ownAddresses)
        addresses.copy(
            avatars =
                addresses.addresses
                    .mapNotNull { email -> ownAvatars.avatar(account.own, email)?.let { EmailAddressKey(email) to it } }
                    .toMap(),
        )
    }

/** "Show mail from", choosing [chosen] for every mail page and pinning addresses as the user asks. */
internal fun MailStates.scopeSwitch(
    chosen: MailScopeChoice,
    onChoose: (MailScopeChoice) -> Unit,
): MailScopeSwitch =
    MailScopeSwitch(
        accountAddresses(),
        chosen,
        addressPins.pins,
        count = { scopeCount(it, behavior.behavior.sidebar.layout.counts, list.feed.messages, folders.all.accounts) },
        onChoose = onChoose,
        onTogglePin = addressPins::toggle,
    )

@Composable
internal fun SidebarScopeSheet(
    states: MailStates,
    chosen: MailScopeChoice,
    onChoose: (MailScopeChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    MailScopeSheet(states.scopeSwitch(chosen, onChoose), onDismiss)
}
