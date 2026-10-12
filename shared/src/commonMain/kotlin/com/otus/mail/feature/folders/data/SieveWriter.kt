package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator

/**
 * Writes [script] as Sieve (RFC 5228) with CRLF line ends. Each enabled rule files a match by
 * folder id (RFC 9042) and stops; the inbox is the fallback when that folder no longer exists, so
 * a deleted folder never loses mail. [metadata] is the line that lets the app read the rules back.
 * [extensions] are the server's, so a server that ties `:mailboxid` to `mailbox` gets it declared.
 */
internal fun writeSieve(
    script: RuleScript,
    metadata: String,
    extensions: Set<String> = emptySet(),
): String {
    val active = script.rules.filter { it.enabled }
    val required = requiredExtensions(active, script.keep, extensions)
    return buildList {
        add(HEADER)
        add(metadata)
        if (required.isNotEmpty()) add("require ${sieveList(required)};")
        active.forEach { rule ->
            val flags = if (rule.markRead) ":flags \"\\\\Seen\" " else ""
            add("")
            add("# ${oneLine(rule.name).ifBlank { "Rule" }}")
            add("if ${test(rule)} {")
            add("    fileinto $flags:mailboxid ${sieveString(rule.folderId)} \"INBOX\";")
            add("    stop;")
            add("}")
        }
        script.keep?.let {
            add("")
            add("include :personal :optional ${sieveString(it)};")
        }
    }.joinToString(CRLF, postfix = CRLF)
}

internal const val HEADER = "# Otus Mail rules. Edit them in Otus Mail; changes made elsewhere are detected."

private const val CRLF = "\r\n"

private const val MAILBOX = "mailbox"

private fun requiredExtensions(
    rules: List<MailRule>,
    keep: String?,
    extensions: Set<String>,
): List<String> {
    val conditions = rules.flatMap { it.conditions }
    return buildSet {
        if (rules.isNotEmpty()) addAll(listOf("fileinto", "mailboxid"))
        // Stalwart refuses `:mailboxid` unless "mailbox" (RFC 5490) is declared too.
        if (rules.isNotEmpty() && MAILBOX in extensions) add(MAILBOX)
        if (rules.any { it.markRead }) add("imap4flags")
        if (conditions.any { it.field == RuleField.Body }) add("body")
        if (conditions.any { it.field == RuleField.AttachmentType || it.field == RuleField.AttachmentName }) add("mime")
        if (conditions.any { it.operator == RuleOperator.MatchesRegex }) add("regex")
        if (keep != null) add("include")
    }.sorted()
}

private fun test(rule: MailRule): String {
    val tests = rule.conditions.map(::test)
    return tests.singleOrNull() ?: "${if (rule.matchAll) "allof" else "anyof"} (${tests.joinToString(", ")})"
}

private fun test(condition: RuleCondition): String {
    val match = matchType(condition)
    val key = key(condition)
    val positive =
        when (val field = condition.field) {
            RuleField.Body -> {
                "body :text $match $key"
            }

            RuleField.AttachmentType -> {
                "header :mime :anychild :contenttype $match \"Content-Type\" $key"
            }

            RuleField.AttachmentName -> {
                "anyof (header :mime :anychild :param \"filename\" $match \"Content-Disposition\" $key, " +
                    "header :mime :anychild :param \"name\" $match \"Content-Type\" $key)"
            }

            else -> {
                // Exact and prefix or suffix comparisons use the address; the rest the whole header.
                val test = if (field.isAddress && condition.operator in ADDRESS_OPERATORS) "address :all" else "header"
                "$test $match ${headers(field)} $key"
            }
        }
    return if (condition.operator.negated) "not $positive" else positive
}

private fun matchType(condition: RuleCondition): String =
    when (condition.operator) {
        RuleOperator.Contains, RuleOperator.DoesNotContain -> ":contains"
        RuleOperator.Is, RuleOperator.IsNot -> ":is"
        RuleOperator.StartsWith, RuleOperator.EndsWith -> ":matches"
        RuleOperator.MatchesRegex -> ":regex"
    }

private fun key(condition: RuleCondition): String {
    val value = oneLine(condition.value)
    return when (condition.operator) {
        RuleOperator.StartsWith -> sieveString(literalWildcards(value) + "*")
        RuleOperator.EndsWith -> sieveString("*" + literalWildcards(value))
        else -> sieveString(value)
    }
}

private fun headers(field: RuleField): String =
    when (field) {
        RuleField.From -> "\"from\""
        RuleField.To -> "\"to\""
        RuleField.Cc -> "\"cc\""
        RuleField.Recipient -> sieveList(listOf("to", "cc"))
        else -> "\"subject\""
    }

private val ADDRESS_OPERATORS =
    setOf(RuleOperator.Is, RuleOperator.IsNot, RuleOperator.StartsWith, RuleOperator.EndsWith)
