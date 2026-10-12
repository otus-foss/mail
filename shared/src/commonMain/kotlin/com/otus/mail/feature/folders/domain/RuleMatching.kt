package com.otus.mail.feature.folders.domain

/** A delivered message as rules see it here: headers and attachment details, without body text. */
data class RuleMessage(
    val from: List<RuleAddress> = emptyList(),
    val to: List<RuleAddress> = emptyList(),
    val cc: List<RuleAddress> = emptyList(),
    val subject: String? = null,
    val attachmentTypes: List<String> = emptyList(),
    val attachmentNames: List<String> = emptyList(),
)

data class RuleAddress(
    val name: String?,
    val email: String,
)

enum class RuleProblem { NoConditions, EmptyValue, ControlCharacter, InvalidRegex, Unsupported, NoFolder }

/** The first reason [rule] cannot be saved for a server with [support], or null when it can. */
fun ruleProblem(
    rule: MailRule,
    support: RuleSupport,
    folderExists: Boolean,
): RuleProblem? =
    when {
        rule.conditions.isEmpty() -> {
            RuleProblem.NoConditions
        }

        rule.conditions.any { it.value.isBlank() } -> {
            RuleProblem.EmptyValue
        }

        (listOf(rule.name, rule.folderId) + rule.conditions.map { it.value }).any { it.hasControlCharacter() } -> {
            RuleProblem.ControlCharacter
        }

        rule.conditions.any { it.operator == RuleOperator.MatchesRegex && it.regexOrNull() == null } -> {
            RuleProblem.InvalidRegex
        }

        rule.conditions.any { !support.supports(it.field) || !support.supports(it.operator) } -> {
            RuleProblem.Unsupported
        }

        rule.markRead && !support.canMarkRead -> {
            RuleProblem.Unsupported
        }

        !folderExists -> {
            RuleProblem.NoFolder
        }

        else -> {
            null
        }
    }

/**
 * Sieve strings cannot hold NUL or a lone CR or LF (RFC 5228, section 2.4.2), and other control
 * characters have no place in a rule. Line breaks are allowed: the script writes them as spaces.
 */
private fun String.hasControlCharacter(): Boolean = any { it < ' ' && it !in "\t\r\n" }

/** Body text is not available here, so such rules can only run on the server. */
val MailRule.checksBody: Boolean
    get() = conditions.any { it.field == RuleField.Body }

/**
 * Whether [this] rule matches [message] as the server would at delivery, for moving mail that
 * arrived before the rule existed. Regular expressions use this platform's dialect, which can
 * differ from the server's in rare cases. Rules that check the body never match here.
 */
fun MailRule.matches(message: RuleMessage): Boolean {
    if (checksBody) return false
    val results = conditions.map { it.matches(message) }
    return if (matchAll) results.all { it } else results.any { it }
}

private fun RuleCondition.matches(message: RuleMessage): Boolean {
    val regex = if (operator == RuleOperator.MatchesRegex) regexOrNull() ?: return false else null
    val compareAddress = field.isAddress && operator !in HEADER_OPERATORS
    val candidates = if (compareAddress) addresses(message).map { it.email } else texts(message)
    val found = candidates.any { text -> test(text, regex) }
    return if (operator.negated) !found else found
}

private fun RuleCondition.test(
    text: String,
    regex: Regex?,
): Boolean =
    when (operator) {
        RuleOperator.Contains, RuleOperator.DoesNotContain -> text.contains(value, ignoreCase = true)
        RuleOperator.Is, RuleOperator.IsNot -> text.equals(value, ignoreCase = true)
        RuleOperator.StartsWith -> text.startsWith(value, ignoreCase = true)
        RuleOperator.EndsWith -> text.endsWith(value, ignoreCase = true)
        RuleOperator.MatchesRegex -> regex?.containsMatchIn(text) == true
    }

/** Each address header the field covers, one list per header line. */
private fun RuleCondition.addressHeaders(message: RuleMessage): List<List<RuleAddress>> =
    when (field) {
        RuleField.From -> listOf(message.from)
        RuleField.To -> listOf(message.to)
        RuleField.Cc -> listOf(message.cc)
        RuleField.Recipient -> listOf(message.to, message.cc)
        else -> emptyList()
    }

private fun RuleCondition.addresses(message: RuleMessage): List<RuleAddress> = addressHeaders(message).flatten()

/** Values as the server compares them: each header line once, display names included. */
private fun RuleCondition.texts(message: RuleMessage): List<String> =
    when (field) {
        RuleField.Subject -> {
            listOfNotNull(message.subject)
        }

        RuleField.AttachmentType -> {
            message.attachmentTypes
        }

        RuleField.AttachmentName -> {
            message.attachmentNames
        }

        RuleField.Body -> {
            emptyList()
        }

        else -> {
            addressHeaders(message).filter { it.isNotEmpty() }.map { line ->
                line.joinToString(", ") { it.header() }
            }
        }
    }

private fun RuleAddress.header(): String = if (name.isNullOrBlank()) email else "$name <$email>"

private fun RuleCondition.regexOrNull(): Regex? = runCatching { Regex(value, RegexOption.IGNORE_CASE) }.getOrNull()

/** Address fields compare whole headers, display names included, for these operators. */
private val HEADER_OPERATORS = setOf(RuleOperator.Contains, RuleOperator.DoesNotContain, RuleOperator.MatchesRegex)
