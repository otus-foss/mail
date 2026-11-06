package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.mailbox.pathText

/** An item placed in its folder tree: [title] is its path below the nearest listed folder above it. */
data class Nested<T>(
    val item: T,
    val title: String,
    val depth: Int,
)

/**
 * Places items in tree order under the nearest earlier item whose path contains theirs, one level
 * deeper. An item whose parent is not listed, such as a folder already chosen elsewhere, keeps the
 * missing part of its path, so "Clients / Acme" stays recognizable without "Clients".
 */
fun <T> List<T>.nestedByPath(path: (T) -> List<String>): List<Nested<T>> =
    fold(emptyList()) { placed, item ->
        val full = path(item)
        val parent =
            placed.lastOrNull { above ->
                path(above.item).let { full.size > it.size && full.take(it.size) == it }
            }
        placed +
            if (parent == null) {
                Nested(item, full.pathText(), depth = 0)
            } else {
                Nested(item, full.drop(path(parent.item).size).pathText(), parent.depth + 1)
            }
    }
