package com.otusfoss.mail.feature.folders.data

import com.otusfoss.mail.feature.folders.domain.FolderRule
import com.otusfoss.mail.feature.folders.domain.RuleCondition
import com.otusfoss.mail.feature.folders.domain.RuleSupport
import com.otusfoss.mail.feature.folders.domain.TextField

/**
 * The index just after the `require` commands that open [script] (RFC 5228, section 3.2), past
 * comments and white space between them and past the rest of the last one's line; 0 when the
 * script does not start with one.
 */
internal fun afterLeadingRequires(script: String): Int =
    generateSequence(Step(next = 0, afterRequire = 0)) { script.step(it.next) }
        .mapNotNull { it.afterRequire }
        .last()

/** One token at the top of a script: where the next one starts, and where a `require` it ended leaves off. */
private class Step(
    val next: Int,
    val afterRequire: Int?,
)

/** Null at the first command that is not a `require`, or at the end. */
private fun String.step(at: Int): Step? =
    when {
        at >= length -> null
        this[at].isWhitespace() -> Step(at + 1, afterRequire = null)
        this[at] == '#' -> Step(lineEnd(at), afterRequire = null)
        startsWith("/*", at) -> afterComment(at)
        isRequireAt(at) -> statementEnd(at)?.let { Step(it, afterStatement(it)) }
        else -> null
    }

internal const val REQUIRE = "require"

private fun String.isRequireAt(at: Int): Boolean =
    regionMatches(at, REQUIRE, 0, REQUIRE.length, ignoreCase = true) &&
        getOrNull(at + REQUIRE.length)?.let { it.isWhitespace() || it == '[' || it == '"' } == true

/** The index after the `;` that ends the command starting at [at], skipping quoted strings; null without one. */
private fun String.statementEnd(at: Int): Int? {
    var index = at
    var quoted = false
    while (index < length) {
        val char = this[index]
        when {
            quoted && char == '\\' -> index++
            char == '"' -> quoted = !quoted
            !quoted && char == ';' -> return index + 1
        }
        index++
    }
    return null
}

/** The end of the line after a command, unless more commands follow on that line. */
private fun String.afterStatement(end: Int): Int {
    val endOfLine = lineEnd(end)
    val rest = substring(end, endOfLine).trim()
    return if (rest.isEmpty() || rest.startsWith('#')) endOfLine else end
}

/** The index after the line end at or after [at], or the text's length. */
private fun String.lineEnd(at: Int): Int = indexOf('\n', at).let { if (it < 0) length else it + 1 }

private fun String.afterComment(at: Int): Step =
    Step(indexOf("*/", at + 2).let { if (it < 0) length else it + 2 }, afterRequire = null)

/** The extensions the Sieve of [folders] uses, for a `require`; none when no folder has a rule. */
internal fun blockExtensions(
    folders: List<BlockFolder>,
    support: RuleSupport,
): List<String> = requiredExtensions(filled(folders).flatMap { it.rules }, support)

private fun requiredExtensions(
    rules: List<FolderRule>,
    support: RuleSupport,
): List<String> {
    val conditions = rules.flatMap { it.conditions }
    val extensions = support.extensions
    return buildSet {
        if (rules.isEmpty()) return@buildSet
        add("fileinto")
        if (support.filesById) {
            add("mailboxid")
            // Stalwart refuses `:mailboxid` unless "mailbox" (RFC 5490) is declared too.
            if ("mailbox" in extensions) add("mailbox")
        }
        if (conditions.any { it is RuleCondition.Text && it.field == TextField.Body }) add("body")
        conditions.filterIsInstance<RuleCondition.When>().forEach {
            add("date")
            if (it.range != null) add("relational")
        }
        if (conditions.any { it is RuleCondition.Spam }) {
            add(if ("spamtest" in extensions) "spamtest" else "spamtestplus")
            add("relational")
            add("comparator-i;ascii-numeric")
        }
    }.sorted()
}
