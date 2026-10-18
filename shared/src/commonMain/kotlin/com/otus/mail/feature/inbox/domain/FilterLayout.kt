package com.otus.mail.feature.inbox.domain

/** A group of choices in the filter panel. */
enum class FilterSection(
    val storageValue: String,
) {
    Show("show"),
    From("from"),
    Accounts("accounts"),
    SentTo("sent_to"),
    ReceivedAs("received_as"),
    InFolder("in_folder"),
    SkipFolders("skip_folders"),
    Date("date"),
    Size("size"),
    Sort("sort"),
}

/**
 * Which filter panel [sections] show, in order, and which Show choices, in order. Settings edit
 * it; sections and choices left out stay out until the user adds them back.
 */
data class FilterLayout(
    val sections: List<FilterSection> = FilterSection.entries,
    val show: List<ShowFacet> = ShowFacet.entries,
) {
    companion object {
        /** [layout] as one line, such as `show,date|unread,flagged`. */
        fun encode(layout: FilterLayout): String =
            layout.sections.joinToString(",") { it.storageValue } + PART +
                layout.show.joinToString(",") { it.storageValue }

        /** Values this version does not know are left out; a line without two parts reads as the default. */
        fun decode(saved: String): FilterLayout {
            val parts = saved.split(PART).takeIf { it.size == 2 } ?: return FilterLayout()
            return FilterLayout(
                sections =
                    parts[0].values().mapNotNull { value ->
                        FilterSection.entries.firstOrNull {
                            it.storageValue ==
                                value
                        }
                    },
                show =
                    parts[1].values().mapNotNull { value ->
                        ShowFacet.entries.firstOrNull { it.storageValue == value }
                    },
            )
        }

        private fun String.values(): List<String> = split(",").filter(String::isNotEmpty).distinct()

        private const val PART = "|"
    }
}
