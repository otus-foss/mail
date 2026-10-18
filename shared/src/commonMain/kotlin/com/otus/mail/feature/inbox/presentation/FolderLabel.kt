package com.otus.mail.feature.inbox.presentation

/**
 * How a folder chip names its folder: the [name], after a [prefix] of parents when another folder
 * has the same name, and [spoken] as its full path. [showAccount] asks for the account's avatar,
 * because a folder of another account has the very same path.
 */
data class FolderLabel(
    val name: String,
    val prefix: String? = null,
    val spoken: String = name,
    val showAccount: Boolean = false,
)

/**
 * Labels for every folder of [accounts]. A folder whose name, ignoring case, no other folder has
 * shows only its name. Otherwise it gets the fewest nearest parents that set it apart, such as
 * "Freelancing / Invoices" beside "Finance / Invoices". More than two parents fold the middle
 * ones into "…", as in "Work / … / Invoices".
 */
fun folderLabels(accounts: List<FilterAccount>): Map<FolderKey, FolderLabel> {
    val all = accounts.flatMap { account -> account.folders.map { account to it } }
    val byName = all.groupBy { (_, folder) -> folder.name.lowercase() }
    return all.associate { (account, folder) ->
        val others = byName.getValue(folder.name.lowercase()).filter { (_, other) -> other.key != folder.key }
        val parents = parentsToShow(folder.ancestors, others.map { (_, other) -> other.ancestors })
        val samePath = others.any { (_, other) -> other.ancestors.equalsIgnoringCase(folder.ancestors) }
        val path = (folder.ancestors + folder.name).joinToString(", ")
        folder.key to
            FolderLabel(
                name = folder.name,
                prefix = parents?.let(::shortPrefix),
                spoken = if (samePath) "$path, ${account.name}" else path,
                showAccount = samePath,
            )
    }
}

/** The fewest nearest [ancestors] that no [others] end with; all of them when none suffice; null when not needed. */
private fun parentsToShow(
    ancestors: List<String>,
    others: List<List<String>>,
): List<String>? {
    if (others.isEmpty()) return null
    val count =
        (1..ancestors.size).firstOrNull { taken ->
            val ending = ancestors.takeLast(taken)
            others.none { it.takeLast(taken).equalsIgnoringCase(ending) }
        } ?: ancestors.size
    return ancestors.takeLast(count).takeIf { it.isNotEmpty() }
}

private fun shortPrefix(parents: List<String>): String =
    if (parents.size > MAX_SHOWN_PARENTS) "${parents.first()} / …" else parents.joinToString(" / ")

private fun List<String>.equalsIgnoringCase(other: List<String>): Boolean =
    size == other.size && zip(other).all { (a, b) -> a.equals(b, ignoreCase = true) }

private const val MAX_SHOWN_PARENTS = 2
