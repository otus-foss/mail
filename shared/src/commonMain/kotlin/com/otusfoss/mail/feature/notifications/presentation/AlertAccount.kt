package com.otusfoss.mail.feature.notifications.presentation

import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.EmailWriter
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.presentation.actions.buttonLabel
import com.otusfoss.mail.core.messages.presentation.actions.offer
import com.otusfoss.mail.core.settings.domain.NOTIFICATION_ACTION_LIMIT
import com.otusfoss.mail.core.settings.domain.NotificationActionChoices

/** What one account lets an alert's buttons do: the roles of its mailboxes, and whether it can send mail. */
data class AlertAccount(
    val mailboxRoles: Set<MailboxRole>,
    val canSend: Boolean,
)
