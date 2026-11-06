package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapFilter
import com.otus.jmap.allOf
import com.otus.jmap.anyOf
import com.otus.jmap.filterCondition
import com.otus.jmap.noneOf
import com.otus.mail.core.mail.inMailbox
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.HeaderOperator
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.TextField
import com.otus.mail.feature.folders.domain.TextOperator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * An `Email/query` filter (RFC 8621, section 4.4.1) for the mail in [mailboxIds] that [rule]
 * matches; null when a condition has no JMAP filter. JMAP searches text, so address and subject
 * conditions compare like "contains": "jana@x" also finds "ojana@x". Conditions that need exact
 * or prefix comparisons, the part before the @, arrival time, or spam scores have no filter.
 */
internal fun emailFilter(
    rule: FolderRule,
    mailboxIds: List<String>,
): JmapFilter? {
    val conditions = rule.conditions.map { it.filter() ?: return null }
    val place = anyOf(mailboxIds.map(::inMailbox))
    return allOf(listOf(place) + conditions)
}

private fun RuleCondition.filter(): JmapFilter? {
    val positive =
        when (this) {
            is RuleCondition.Address -> addressFilter()
            is RuleCondition.Text -> textFilter()
            is RuleCondition.Header -> if (operator == HeaderOperator.Contains) header(name, value) else null
            is RuleCondition.MailingList -> header("List-Id", "<$listId>")
            is RuleCondition.Size -> sizeFilter()
            is RuleCondition.When, is RuleCondition.Spam -> null
        } ?: return null
    return if (negated) noneOf(listOf(positive)) else positive
}

private fun RuleCondition.Address.addressFilter(): JmapFilter? {
    val text =
        when (match) {
            is AddressMatch.Exact -> match.address
            is AddressMatch.Domain -> "@" + match.domain
            is AddressMatch.LocalPart -> return null
        }
    return filterCondition(field.header, JsonPrimitive(text))
}

private fun RuleCondition.Text.textFilter(): JmapFilter? =
    when {
        operator != TextOperator.Contains -> null
        field == TextField.Subject -> filterCondition("subject", JsonPrimitive(value))
        else -> filterCondition("body", JsonPrimitive(value))
    }

/** Sieve's `:over` is strictly larger and `:under` strictly smaller; JMAP's `minSize` includes its value. */
private fun RuleCondition.Size.sizeFilter(): JmapFilter =
    if (over) filterCondition("minSize", JsonPrimitive(bytes + 1)) else filterCondition("maxSize", JsonPrimitive(bytes))

private fun header(
    name: String,
    value: String,
) = filterCondition("header", JsonArray(listOf(JsonPrimitive(name), JsonPrimitive(value))))
