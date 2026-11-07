package com.otus.mail.feature.notifications.presentation

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.EmailWriter
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.presentation.actions.buttonLabel
import com.otus.mail.core.messages.presentation.actions.offer
import com.otus.mail.core.settings.domain.NOTIFICATION_ACTION_LIMIT
import com.otus.mail.core.settings.domain.NotificationActionChoices

/** What one account lets an alert's buttons do: the roles of its mailboxes, and whether it can send mail. */
data class AlertAccount(
    val mailboxRoles: Set<MailboxRole>,
    val canSend: Boolean,
)
