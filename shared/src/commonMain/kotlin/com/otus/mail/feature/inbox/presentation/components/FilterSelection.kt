package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.selectedAccounts
import com.otus.mail.feature.inbox.domain.selectedSenders

/** The senders and accounts behind the applied filters, resolved from the loaded messages. */
data class FilterSelection(
    val senders: List<MailSender> = emptyList(),
    val accounts: List<MailLocation> = emptyList(),
)

@Composable
internal fun rememberFilterSelection(
    messages: List<MailMessage>,
    search: InboxSearch,
    accounts: List<MailLocation>? = null,
): FilterSelection =
    remember(messages, search.senders, search.accounts, accounts) {
        FilterSelection(
            selectedSenders(messages, search.senders),
            selectedAccounts(messages, search.accounts, accounts),
        )
    }
