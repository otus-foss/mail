package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage

/** The page's loaded [messages] and the [filters] applied to them. */
internal class ListedMail(
    val messages: List<MailMessage>,
    val filters: InboxFilters,
)
