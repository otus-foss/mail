package com.otusfoss.mail.feature.folders.data

import com.otusfoss.mail.feature.folders.domain.AddressField
import com.otusfoss.mail.feature.folders.domain.AddressMatch
import com.otusfoss.mail.feature.folders.domain.FolderRule
import com.otusfoss.mail.feature.folders.domain.HeaderOperator
import com.otusfoss.mail.feature.folders.domain.RuleCondition
import com.otusfoss.mail.feature.folders.domain.RuleSupport
import com.otusfoss.mail.feature.folders.domain.TextField
import com.otusfoss.mail.feature.folders.domain.TextOperator

/**
 * The Sieve (RFC 5228) inside the Otus block, one line per entry, for [folders] in order. Each
 * folder's rules become one `if anyof (…)` that files the message and stops, so the first folder
 * that matches wins. With `mailboxid` (RFC 9042) a folder is filed by id and its [BlockFolder.path]
 * is the fallback the server uses when the id no longer exists; without it, by that path alone.
 * [zone] is the UTC offset, such as `+0200`, that day and time conditions use. The extensions the
 * lines use come from [blockExtensions], since a `require` must open the script.
 */
internal fun sieveLines(
    folders: List<BlockFolder>,
    support: RuleSupport,
    zone: String,
): List<String> =
    filled(folders).flatMap { folder ->
        val tests = folder.rules.map { it.test(zone) }
        listOf(
            "if ${tests.singleOrNull() ?: anyOf(tests)} {",
            "    ${fileInto(folder, support)};",
            "    stop;",
            "}",
        )
    }

/** [folders] with only the rules that have conditions, and only the folders left with rules. */
internal fun filled(folders: List<BlockFolder>): List<BlockFolder> =
    folders
        .map { folder -> folder.copy(rules = folder.rules.filter { it.conditions.isNotEmpty() }) }
        .filter { it.rules.isNotEmpty() }

/** A folder in the block: its rules, and the path of names a server without `mailboxid` files into. */
internal data class BlockFolder(
    val id: String,
    val path: String,
    val rules: List<FolderRule>,
)

/**
 * Nested folders are named by their full path with `/` between names, which is Stalwart's
 * hierarchy separator. JMAP does not report a separator (RFC 8621 has none), so other servers
 * may differ; they only need the path when they lack `mailboxid`.
 */
internal const val FOLDER_PATH_SEPARATOR = "/"

private fun fileInto(
    folder: BlockFolder,
    support: RuleSupport,
): String =
    if (support.filesById) {
        "fileinto :mailboxid ${sieveString(folder.id)} ${sieveString(folder.path)}"
    } else {
        "fileinto ${sieveString(folder.path)}"
    }

private fun FolderRule.test(zone: String): String {
    val tests = conditions.map { it.test(zone) }
    return tests.singleOrNull() ?: allOf(tests)
}

internal fun anyOf(tests: List<String>) = tests.joinToString(", ", "anyof (", ")")

internal fun allOf(tests: List<String>) = tests.joinToString(", ", "allof (", ")")

private fun RuleCondition.test(zone: String): String {
    val positive =
        when (this) {
            is RuleCondition.Address -> addressTest()
            is RuleCondition.Text -> textTest()
            is RuleCondition.Header -> headerTest()
            is RuleCondition.MailingList -> "header :contains \"list-id\" ${sieveString("<${oneLine(listId)}>")}"
            is RuleCondition.Size -> "size ${if (over) ":over" else ":under"} $bytes"
            is RuleCondition.When -> whenTest(zone)
            is RuleCondition.Spam -> "spamtest :value \"ge\" :comparator \"i;ascii-numeric\" \"$minScore\""
        }
    return if (negated) "not $positive" else positive
}

private fun RuleCondition.Address.addressTest(): String {
    val part =
        when (match) {
            is AddressMatch.Exact -> ":all"
            is AddressMatch.Domain -> ":domain"
            is AddressMatch.LocalPart -> ":localpart"
        }
    return "address $part :is ${sieveString(field.header)} ${sieveString(oneLine(match.value))}"
}

internal val AddressField.header: String
    get() = name.lowercase()

private fun RuleCondition.Text.textTest(): String {
    val text = oneLine(value)
    val (match, key) =
        when (operator) {
            TextOperator.Contains -> ":contains" to text
            TextOperator.StartsWith -> ":matches" to literalWildcards(text) + "*"
            TextOperator.Matches -> ":matches" to text
        }
    return when (field) {
        TextField.Subject -> "header $match \"subject\" ${sieveString(key)}"
        TextField.Body -> "body :text $match ${sieveString(key)}"
    }
}

private fun RuleCondition.Header.headerTest(): String {
    val match = if (operator == HeaderOperator.Is) ":is" else ":contains"
    return "header $match ${sieveString(oneLine(name))} ${sieveString(oneLine(value))}"
}
