package com.otus.mail.feature.folders.domain

/**
 * For each folder id, the senders whose mail a rule moves there, in rule order and once each
 * regardless of letter case.
 */
fun AccountFolderRules.sendersByFolder(): Map<String, List<String>> =
    rules
        .mapValues { (_, folderRules) -> folderRules.flatMap { it.senders() }.distinctBy { it.lowercase() } }
        .filterValues { it.isNotEmpty() }

/**
 * The address this rule moves every message from: a single "From is" condition. A group that also
 * needs another condition moves only some of a sender's mail.
 */
fun FolderRule.senders(): List<String> {
    val condition = conditions.singleOrNull() as? RuleCondition.Address ?: return emptyList()
    val sender = condition.takeIf { it.field == AddressField.From && !it.negated }
    val match = sender?.match as? AddressMatch.Exact
    val address = match?.address?.trim()
    return listOfNotNull(address?.takeIf { it.isNotEmpty() })
}
