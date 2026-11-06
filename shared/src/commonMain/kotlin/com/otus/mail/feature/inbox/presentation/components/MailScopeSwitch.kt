package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.core.messages.domain.AccountAddresses
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.settings.domain.AddressPins
import com.otus.mail.feature.inbox.presentation.ScopeHeader
import com.otus.mail.feature.inbox.presentation.scopeHeader

/**
 * What "Show mail from" chooses between: every account, one account, or one address. [count]
 * gives each choice's count, or null for none. Choosing applies at once; a long press on an
 * address calls [onTogglePin].
 */
class MailScopeSwitch(
    val accounts: List<AccountAddresses>,
    val chosen: MailScopeChoice,
    val pins: AddressPins,
    val count: (MailScopeChoice) -> String?,
    val onChoose: (MailScopeChoice) -> Unit,
    val onTogglePin: (MailScopeChoice.Address) -> Unit,
) {
    /** How the chosen scope is named and pictured, such as in the inbox's account stack. */
    val header: ScopeHeader
        get() = scopeHeader(chosen, accounts)
}
