package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender

/**
 * The senders the From section offers: each [chosen] address first, named from [messages] when
 * they hold it, then whoever sent the most of [messages], up to [limit] in all.
 */
fun filterSenders(
    messages: List<MailMessage>,
    chosen: Set<String>,
    limit: Int = MAX_SENDERS,
): List<MailSender> {
    val byAddress = messages.map { it.sender }.groupBy { it.address.lowercase() }
    val picked = chosen.map { address -> byAddress[address.lowercase()]?.first() ?: MailSender("", address) }
    val frequent =
        byAddress.values
            .sortedByDescending { it.size }
            .map { it.first() }
            .filter { sender -> chosen.none { it.equals(sender.address, ignoreCase = true) } }
    return picked + frequent.take((limit - picked.size).coerceAtLeast(0))
}

private const val MAX_SENDERS = 6
