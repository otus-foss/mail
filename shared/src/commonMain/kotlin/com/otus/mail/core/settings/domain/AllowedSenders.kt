package com.otus.mail.core.settings.domain

import com.otus.mail.core.mail.EmailAddressKey

/** [address] added to [MailBehavior.allowedSenders], lowercase, newest first, and listed once. */
fun MailBehavior.allowingSender(address: String): MailBehavior {
    val allowed = EmailAddressKey(address).value
    if (allowed.isEmpty()) return this
    return copy(allowedSenders = listOf(allowed) + allowedSenders.filterNot { it == allowed })
}

/** [address] taken out of [MailBehavior.allowedSenders], so its images ask first again. */
fun MailBehavior.withoutSender(address: String): MailBehavior =
    copy(allowedSenders = allowedSenders.filterNot { it.equals(address.trim(), ignoreCase = true) })
