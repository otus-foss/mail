package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.HeaderOperator
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.TextField
import com.otus.mail.feature.folders.domain.TextOperator
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The name of the old separate rules script, which a new account also gets when it has no script. */
internal const val RULES_SCRIPT = "Otus Mail"

/** Rules from the old "Otus Mail" script, by folder id in their old order, and the script it kept running. */
internal data class LegacyRules(
    val rules: Map<String, List<FolderRule>>,
    val keep: String?,
)

/**
 * The rules in an old `# otus-rules-v1 {…}` script, converted; null when [text] has no such line.
 * Paused rules, regular expressions, attachment conditions, "mark as read", and rule names have no
 * place in folder rules and are dropped. A condition that cannot be converted drops its whole rule
 * when every condition had to match, since the rest alone would catch more mail, and only itself
 * when any condition was enough.
 */
internal fun readLegacyRules(text: String): LegacyRules? {
    val line =
        text.lineSequence().map { it.trimEnd('\r') }.firstOrNull { it.startsWith(LEGACY_PREFIX) } ?: return null
    val dto =
        try {
            json.decodeFromString(LegacyScriptDto.serializer(), line.removePrefix(LEGACY_PREFIX))
        } catch (expected: SerializationException) {
            null
        } catch (expected: IllegalArgumentException) {
            null
        }
    val rules =
        dto
            ?.rules
            .orEmpty()
            .filter { it.enabled }
            .flatMap { rule -> rule.toFolderRules().map { rule.folder to it } }
            .groupBy({ it.first }, { it.second })
    return LegacyRules(rules, dto?.keep)
}

private const val LEGACY_PREFIX = "# otus-rules-v1 "

/** The most folder rules one old rule may split into; the old script is not trusted to be small. */
private const val MAX_SPLIT_RULES = 64

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private class LegacyScriptDto(
    val rules: List<LegacyRuleDto> = emptyList(),
    val keep: String? = null,
)

@Serializable
private class LegacyRuleDto(
    val conditions: List<LegacyConditionDto>,
    val folder: String,
    val matchAll: Boolean = true,
    val enabled: Boolean = true,
)

@Serializable
private class LegacyConditionDto(
    val field: String,
    val operator: String,
    val value: String,
)

/**
 * "To or Cc" becomes two alternatives, one for To and one for Cc; "neither To nor Cc" becomes one
 * group of both. When every condition had to match, the rule splits into one rule per combination
 * of alternatives, which together match the same mail. The combinations double with each "To or
 * Cc", so a rule that would split into more than [MAX_SPLIT_RULES] is dropped instead.
 */
private fun LegacyRuleDto.toFolderRules(): List<FolderRule> {
    val converted = conditions.map { it.alternatives() }
    return if (matchAll) {
        if (converted.any { it.isEmpty() }) return emptyList()
        val combinations =
            converted.fold(1) { count, options -> (count * options.size).coerceAtMost(MAX_SPLIT_RULES + 1) }
        if (combinations > MAX_SPLIT_RULES) return emptyList()
        converted
            .fold(listOf(emptyList<RuleCondition>())) { rules, options ->
                rules.flatMap { rule ->
                    options.map {
                        rule +
                            it
                    }
                }
            }.map(::FolderRule)
    } else {
        converted.flatten().map(::FolderRule)
    }
}

/** Groups of conditions that together replace this one, any of which may match; empty when it has no equivalent. */
private fun LegacyConditionDto.alternatives(): List<List<RuleCondition>> {
    val negated = operator == "doesNotContain" || operator == "isNot"
    val single =
        when (field) {
            "from" -> {
                address(AddressField.From, negated)
            }

            "to" -> {
                address(AddressField.To, negated)
            }

            "cc" -> {
                address(AddressField.Cc, negated)
            }

            "subject" -> {
                subject(negated)
            }

            "body" -> {
                RuleCondition.Text(TextField.Body, TextOperator.Contains, value, negated).takeIf {
                    operator !=
                        "matchesRegex"
                }
            }

            else -> {
                null
            }
        }
    if (field != "recipient") return listOfNotNull(single).map(::listOf)
    val both = listOfNotNull(address(AddressField.To, negated), address(AddressField.Cc, negated))
    return when {
        both.size < 2 -> emptyList()
        negated -> listOf(both)
        else -> both.map(::listOf)
    }
}

/**
 * "is" keeps the address, "ends with @domain" the domain, and "starts with name@" the part
 * before the @. Other comparisons tested the whole header, display names included, and become a
 * header condition; "ends with" without an @ becomes "contains", which can catch a little more.
 */
private fun LegacyConditionDto.address(
    field: AddressField,
    negated: Boolean,
): RuleCondition? {
    val match = addressMatch()
    return when {
        operator == "matchesRegex" -> null
        match != null -> RuleCondition.Address(field, match, negated)
        else -> RuleCondition.Header(field.name, HeaderOperator.Contains, value, negated)
    }
}

private fun LegacyConditionDto.addressMatch(): AddressMatch? =
    when {
        operator == "is" || operator == "isNot" -> {
            if (value.startsWith('@')) AddressMatch.Domain(value.drop(1)) else AddressMatch.Exact(value)
        }

        operator == "endsWith" && value.startsWith('@') -> {
            AddressMatch.Domain(value.drop(1))
        }

        operator == "startsWith" && value.endsWith('@') -> {
            AddressMatch.LocalPart(value.dropLast(1))
        }

        else -> {
            null
        }
    }

private fun LegacyConditionDto.subject(negated: Boolean): RuleCondition? =
    when (operator) {
        "contains", "doesNotContain" -> RuleCondition.Text(TextField.Subject, TextOperator.Contains, value, negated)
        "startsWith" -> RuleCondition.Text(TextField.Subject, TextOperator.StartsWith, value)
        "endsWith" -> RuleCondition.Text(TextField.Subject, TextOperator.Matches, "*" + literalWildcards(value))
        "is", "isNot" -> RuleCondition.Header("Subject", HeaderOperator.Is, value, negated)
        else -> null
    }
