package com.otus.mail.feature.folders.domain

/**
 * For each folder id, the senders whose mail a running rule moves there, in rule order and once
 * each regardless of letter case. Paused rules move nothing, so they are left out.
 */
fun AccountRules.sendersByFolder(): Map<String, List<String>> =
    rules
        .filter { it.enabled }
        .flatMap { rule -> rule.senders().map { rule.folderId to it } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, senders) -> senders.distinctBy { it.lowercase() } }

/**
 * The addresses this rule moves every message from: a "from is" condition that matches on its
 * own. A rule that also needs another condition moves only some of a sender's mail.
 */
fun MailRule.senders(): List<String> =
    if (matchAll && conditions.size > 1) {
        emptyList()
    } else {
        conditions
            .filter { it.field == RuleField.From && it.operator == RuleOperator.Is }
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
    }
