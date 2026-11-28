package com.otusfoss.mail.feature.contacts.presentation

import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.PersonHistory
import com.otusfoss.mail.core.messages.domain.PersonThread

/**
 * What the app gives the person sheet about the person's mail: how much there is, as the list it
 * opens counts it; their [history]; the thread the user reads, [currentThreadId]; and the ways
 * to leave the sheet for mail.
 */
internal class PersonMail(
    /** How many messages from the person the list shows, only the unread ones when asked; null when unknown. */
    val count: suspend (unread: Boolean) -> Long?,
    val history: suspend () -> PersonHistory?,
    val currentThreadId: String?,
    val write: () -> Unit,
    /** Shows the person's mail in the list, only the unread mail when asked. */
    val showMail: (unread: Boolean) -> Unit,
    /** Shows the person's mail that also names [MailSender]. */
    val showWith: (MailSender) -> Unit,
    val openThread: (PersonThread) -> Unit,
)
