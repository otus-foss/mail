package com.otusfoss.mail.core.settings.domain

import com.otusfoss.mail.core.mail.EmailAddressKey

/** [address] added to [ReadBehavior.allowedSenders], lowercase, newest first, and listed once. */
fun ReadBehavior.allowingSender(address: String): ReadBehavior {
    val allowed = EmailAddressKey(address).value
    if (allowed.isEmpty()) return this
    return copy(allowedSenders = listOf(allowed) + allowedSenders.filterNot { it == allowed })
}

/** [address] taken out of [ReadBehavior.allowedSenders], so its images ask first again. */
fun ReadBehavior.withoutSender(address: String): ReadBehavior =
    copy(allowedSenders = allowedSenders.filterNot { EmailAddressKey(it) == EmailAddressKey(address) })
