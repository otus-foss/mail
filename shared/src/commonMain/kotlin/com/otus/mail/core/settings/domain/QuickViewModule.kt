package com.otus.mail.core.settings.domain

/**
 * What the inbox's quick view shows above its Jump to row, when the control panel is pulled down.
 * Each module always has something in it, so the quick view never shows an empty card.
 */
enum class QuickViewModule(
    val storageValue: String,
) {
    /** Needs reply, Waiting, Flagged, and Later, each with its count. */
    Counts("counts"),

    /** The people the user deals with most, with a dot for unread mail from them. */
    People("people"),

    /** The user's own addresses with their unread counts, to show only one of them. */
    Addresses("addresses"),

    /** The newest files that came with mail. */
    RecentFiles("recent_files"),
    ;

    companion object {
        /** At most this many modules show, so the quick view keeps one height. */
        const val LIMIT: Int = 2

        val Defaults: List<QuickViewModule> = listOf(Counts, People)

        /** [modules] as one line, such as `counts,people`. */
        fun encode(modules: List<QuickViewModule>): String = modules.joinToString(",") { it.storageValue }

        /** Values this version does not know are left out, and only the first [LIMIT] count. */
        fun decode(saved: String): List<QuickViewModule> =
            decodeModules(saved, entries, QuickViewModule::storageValue, LIMIT)
    }
}

/**
 * The [entries] a saved list of modules names in order, such as `counts,people`: values this
 * version does not know are left out, repeats count once, and only the first [limit] count.
 */
fun <T> decodeModules(
    saved: String,
    entries: List<T>,
    storageValue: (T) -> String,
    limit: Int,
): List<T> =
    saved
        .split(",")
        .mapNotNull { value -> entries.firstOrNull { storageValue(it) == value } }
        .distinct()
        .take(limit)
