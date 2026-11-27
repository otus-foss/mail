package com.otusfoss.mail.core.text

/**
 * The names of the [affected] accounts, for copy about some of the user's [total] accounts, as in
 * "Work and Personal". Null when a name tells the user nothing: they have one account, or every
 * account is affected.
 */
fun accountLabel(
    affected: List<String>,
    total: Int,
): String? = affected.takeIf { total > 1 && it.size < total }?.let(::andList)

/** "Work", "Work and Personal", "Work, Personal and Home". */
private fun andList(names: List<String>): String =
    if (names.size == 1) names.single() else "${names.dropLast(1).joinToString(", ")} and ${names.last()}"

/** Names every one of the user's accounts at once, where they have several. */
const val ALL_ACCOUNTS = "All accounts"
