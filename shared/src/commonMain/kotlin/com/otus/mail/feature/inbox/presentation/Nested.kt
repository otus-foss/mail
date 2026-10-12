package com.otus.mail.feature.inbox.presentation

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
fun <T> List<T>.nestedByPath(path: (T) -> String): List<Nested<T>> =
    fold(emptyList()) { placed, item ->
        val full = path(item)
        val parent = placed.lastOrNull { full.startsWith(path(it.item) + PATH_SEPARATOR) }
        placed +
            if (parent == null) {
                Nested(item, full, 0)
            } else {
                Nested(item, full.removePrefix(path(parent.item) + PATH_SEPARATOR), parent.depth + 1)
            }
    }

// The folders feature joins a folder's path with this separator.
private const val PATH_SEPARATOR = " / "
