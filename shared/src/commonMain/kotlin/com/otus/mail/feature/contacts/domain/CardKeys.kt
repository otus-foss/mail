package com.otus.mail.feature.contacts.domain

/**
 * Keys such as `i1`, `i2` that [taken] does not hold, in order, so a new entry never replaces one
 * a card keeps.
 */
internal fun freeKeys(
    prefix: String,
    taken: Set<String>,
    suffix: String = "",
): Sequence<String> = generateSequence(1) { it + 1 }.map { "$prefix$it$suffix" }.filterNot { it in taken }
