package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.TextField
import com.otus.mail.feature.folders.domain.TextOperator

/** An address seen once in loaded mail, as [field]: the sender of one message or one of its recipients. */
data class KnownAddress(
    val field: AddressField,
    val address: String,
    val name: String = "",
)

/** One suggestion in the Add rule sheet: [title] over [subtitle], and the condition it adds. */
data class RuleSuggestion(
    val title: String,
    val subtitle: String?,
    val condition: RuleCondition,
)

/**
 * Suggestions for a From or To rule that match [query]: each address whose address or name
 * matches, and "Anyone at @domain" for their domains, most messages first, from [known] mail. A
 * typed address, "@domain", or "name@" is offered too when loaded mail has none like it. Subject
 * and Mailing list rules take the typed text instead, so they get none.
 */
fun ruleSuggestions(
    kind: RuleKind,
    query: String,
    known: List<KnownAddress>,
): List<RuleSuggestion> {
    val field = kind.field ?: return emptyList()
    val needle = query.trim().removePrefix("@")
    val seen = known.filter { it.field == field && '@' in it.address }
    val matches = { mail: KnownAddress -> mail.address.contains(needle, true) || mail.name.contains(needle, true) }
    val domains =
        seen
            .groupBy { it.address.substringAfterLast('@').lowercase() }
            .filter { (_, mail) -> mail.any(matches) }
            .map { (domain, mail) -> Counted(domainSuggestion(field, domain, messages(mail.size)), mail.size, 0) }
    val addresses =
        seen
            .groupBy { it.address.lowercase() }
            .filterValues { mail -> mail.any(matches) }
            .map { (_, mail) ->
                val name = mail.firstNotNullOfOrNull { it.name.takeIf(String::isNotBlank) }
                val subtitle = listOfNotNull(name, messages(mail.size)).joinToString(" · ")
                val condition = RuleCondition.Address(field, AddressMatch.Exact(mail.first().address))
                Counted(RuleSuggestion(mail.first().address, subtitle, condition), mail.size, 1)
            }
    val found =
        (domains + addresses)
            .sortedWith(compareByDescending<Counted> { it.count }.thenBy { it.order }.thenBy { it.suggestion.title })
            .map { it.suggestion }
            .take(MAX_SUGGESTIONS)
    val typed = typedAddress(field, query)?.takeIf { typed -> found.none { it.condition == typed.condition } }
    return listOfNotNull(typed) + found
}

/** The condition a Subject or Mailing list rule makes from the typed text; null for the address kinds. */
fun typedCondition(
    kind: RuleKind,
    query: String,
): RuleCondition? {
    val text = query.trim().takeIf { it.isNotEmpty() } ?: return null
    return when (kind) {
        RuleKind.Subject -> RuleCondition.Text(TextField.Subject, TextOperator.Contains, text)
        RuleKind.MailingList -> RuleCondition.MailingList(text.removePrefix("<").removeSuffix(">"))
        RuleKind.From, RuleKind.To -> null
    }
}

private class Counted(
    val suggestion: RuleSuggestion,
    val count: Int,
    /** Domains come before addresses with as many messages. */
    val order: Int,
)

private fun domainSuggestion(
    field: AddressField,
    domain: String,
    subtitle: String?,
) = RuleSuggestion("Anyone at @$domain", subtitle, RuleCondition.Address(field, AddressMatch.Domain(domain)))

/** What the user typed as an address rule: "@domain", "name@" for any domain, or one address. */
private fun typedAddress(
    field: AddressField,
    query: String,
): RuleSuggestion? {
    val text = query.trim()
    val local = text.substringBefore('@')
    val domain = text.substringAfter('@', "")
    return when {
        text.count { it == '@' } != 1 || ' ' in text -> {
            null
        }

        local.isEmpty() && '.' in domain -> {
            domainSuggestion(field, domain.lowercase(), null)
        }

        domain.isEmpty() && local.isNotEmpty() -> {
            RuleSuggestion("$local@ any domain", null, RuleCondition.Address(field, AddressMatch.LocalPart(local)))
        }

        local.isNotEmpty() && '.' in domain -> {
            RuleSuggestion(text, null, RuleCondition.Address(field, AddressMatch.Exact(text)))
        }

        else -> {
            null
        }
    }
}

private fun messages(count: Int): String = if (count == 1) "1 message" else "$count messages"

/** Long lists push the preview and the switch out of sight. */
private const val MAX_SUGGESTIONS = 6
