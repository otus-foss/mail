package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.HeaderOperator
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.TextField
import com.otus.mail.feature.folders.domain.TextOperator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * An `Email/query` filter (RFC 8621, section 4.4.1) for the mail in [mailboxIds] that [rule]
 * matches; null when a condition has no JMAP filter. JMAP searches text, so address and subject
 * conditions compare like "contains": "jana@x" also finds "ojana@x". Conditions that need exact
 * or prefix comparisons, the part before the @, arrival time, or spam scores have no filter.
 */
internal fun emailFilter(
    rule: FolderRule,
    mailboxIds: List<String>,
): JsonObject? {
    val conditions = rule.conditions.map { it.filter() ?: return null }
    val places = mailboxIds.map { id -> buildJsonObject { put("inMailbox", JsonPrimitive(id)) } }
    val place = places.singleOrNull() ?: operator("OR", places)
    return operator("AND", listOf(place) + conditions)
}

private fun RuleCondition.filter(): JsonObject? {
    val positive =
        when (this) {
            is RuleCondition.Address -> addressFilter()
            is RuleCondition.Text -> textFilter()
            is RuleCondition.Header -> if (operator == HeaderOperator.Contains) header(name, value) else null
            is RuleCondition.MailingList -> header("List-Id", "<$listId>")
            is RuleCondition.Size -> sizeFilter()
            is RuleCondition.When, is RuleCondition.Spam -> null
        } ?: return null
    return if (negated) operator("NOT", listOf(positive)) else positive
}

private fun RuleCondition.Address.addressFilter(): JsonObject? {
    val text =
        when (match) {
            is AddressMatch.Exact -> match.address
            is AddressMatch.Domain -> "@" + match.domain
            is AddressMatch.LocalPart -> return null
        }
    return property(field.header, JsonPrimitive(text))
}

private fun RuleCondition.Text.textFilter(): JsonObject? =
    when {
        operator != TextOperator.Contains -> null
        field == TextField.Subject -> property("subject", JsonPrimitive(value))
        else -> property("body", JsonPrimitive(value))
    }

/** Sieve's `:over` is strictly larger and `:under` strictly smaller; JMAP's `minSize` includes its value. */
private fun RuleCondition.Size.sizeFilter(): JsonObject =
    if (over) property("minSize", JsonPrimitive(bytes + 1)) else property("maxSize", JsonPrimitive(bytes))

private fun header(
    name: String,
    value: String,
) = property("header", JsonArray(listOf(JsonPrimitive(name), JsonPrimitive(value))))

private fun property(
    name: String,
    value: JsonElement,
) = buildJsonObject { put(name, value) }

private fun operator(
    name: String,
    conditions: List<JsonObject>,
) = buildJsonObject {
    put("operator", JsonPrimitive(name))
    put("conditions", JsonArray(conditions))
}
