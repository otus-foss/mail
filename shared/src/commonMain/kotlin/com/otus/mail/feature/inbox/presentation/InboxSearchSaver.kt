package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.saveable.mapSaver
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailboxScope
import com.otus.mail.feature.inbox.domain.SearchField

internal val InboxSearchSaver =
    mapSaver(
        save = {
            mapOf(
                "query" to it.query,
                "senders" to it.senders.toList(),
                "start" to it.dateStart,
                "end" to it.dateEnd,
                "mailboxNames" to it.mailboxes.map { scope -> scope.name },
                "mailboxAccounts" to it.mailboxes.map { scope -> scope.account },
                "accounts" to it.accounts.toList(),
                "field" to it.field.name,
                "withFiles" to it.withFiles,
            )
        },
        restore = { values ->
            val names = (values["mailboxNames"] as List<*>).map { it as String }
            val accounts = (values["mailboxAccounts"] as List<*>).map { it as String? }
            InboxSearch(
                query = values["query"] as String,
                senders = (values["senders"] as List<*>).map { it as String }.toSet(),
                dateStart = values["start"] as String?,
                dateEnd = values["end"] as String?,
                mailboxes = names.zip(accounts) { name, account -> MailboxScope(account, name) }.toSet(),
                accounts = (values["accounts"] as List<*>).map { it as String }.toSet(),
                field = SearchField.valueOf(values["field"] as String),
                withFiles = values["withFiles"] as Boolean,
            )
        },
    )
