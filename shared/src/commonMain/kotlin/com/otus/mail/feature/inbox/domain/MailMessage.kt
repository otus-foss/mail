package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.avatar.ResolvedAvatar

/** Portable, supplied message data. No account connection or persistence is implied. */
data class MailMessage(
    val id: String,
    val sender: MailSender,
    val subject: String,
    val body: String,
    val received: ReceivedAt,
    val location: MailLocation,
    val attributes: MailAttributes = MailAttributes(),
)

data class MailSender(
    val name: String,
    val address: String,
    val accountKey: String = "",
    val avatar: ResolvedAvatar? = null,
)

/** ISO calendar date (YYYY-MM-DD) and 24-hour time (HH:mm), supplied by the data source. */
data class ReceivedAt(
    val date: String,
    val time: String,
)

data class MailLocation(
    val mailbox: String,
    val account: String,
    /** Stable login identity; the display name is not unique. */
    val accountId: String = account,
    val accountDetail: String = "",
)

data class MailAttributes(
    val unread: Boolean = false,
    val attachments: List<String> = emptyList(),
)
