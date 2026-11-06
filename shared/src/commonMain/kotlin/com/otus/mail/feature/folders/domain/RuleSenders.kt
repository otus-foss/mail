package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mail.EmailAddressKey

/**
 * For each folder id, the senders whose mail a rule moves there, in rule order and once each
 * regardless of letter case.
 */
fun AccountFolderRules.sendersByFolder(): Map<String, List<String>> =
    rules
        .mapValues { (_, folderRules) -> folderRules.flatMap { it.senders() }.distinctBy { EmailAddressKey(it) } }
        .filterValues { it.isNotEmpty() }

/**
 * The address this rule moves every message from: a single "From is" condition. A group that also
 * needs another condition moves only some of a sender's mail.
 */
fun FolderRule.senders(): List<String> =
    conditions
        .singleOrNull()
        ?.let { it as? RuleCondition.Address }
        ?.takeIf { it.field == AddressField.From && !it.negated }
        ?.let { (it.match as? AddressMatch.Exact)?.address?.trim() }
        ?.takeIf { it.isNotEmpty() }
        .let(::listOfNotNull)
