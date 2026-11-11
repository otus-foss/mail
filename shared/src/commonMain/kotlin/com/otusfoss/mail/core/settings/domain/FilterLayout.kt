package com.otusfoss.mail.core.settings.domain

import com.otusfoss.mail.core.messages.domain.QuickFacet

/** A group of choices on the filter panel's main page. */
enum class FilterSection(
    val storageValue: String,
) {
    Status("status"),
    Quick("quick"),
    People("people"),

    /** The Account, Folders, and Date rows, which open popups, and the Size toggle. */
    Rows("rows"),
    Sort("sort"),
}

/**
 * Which filter panel [sections] show, in order, and which [quick] filters, in order. Settings edit
 * it; sections and choices left out stay out until the user adds them back.
 */
data class FilterLayout(
    val sections: List<FilterSection> = FilterSection.entries,
    val quick: List<QuickFacet> = QuickFacet.entries,
) {
    companion object {
        /** [layout] as one line, such as `status,rows|flagged,attachments`. */
        fun encode(layout: FilterLayout): String =
            layout.sections.joinToString(",") { it.storageValue } + PART +
                layout.quick.joinToString(",") { it.storageValue }

        /**
         * Values this version does not know are left out; a line without two parts reads as the
         * default. A layout of the earlier panel, whose sections and Show choices differ, keeps
         * its order: each old section becomes the new one that holds its choices, and a new
         * choice that replaced none, such as Needs my reply, is added at the end.
         */
        fun decode(saved: String): FilterLayout {
            val parts = saved.split(PART).takeIf { it.size == 2 } ?: return FilterLayout()
            val sections = parts[0].values()
            val quick = parts[1].values()
            val legacy = sections.any { it in LEGACY_SECTIONS } || quick.any { it in LEGACY_QUICK }
            val decoded =
                FilterLayout(
                    sections = sections.flatMap(::sectionsOf).distinct(),
                    quick = quick.mapNotNull(::quickOf).distinct(),
                )
            return if (legacy) decoded.copy(quick = (decoded.quick + ADDED_QUICK).distinct()) else decoded
        }

        private fun sectionsOf(value: String): List<FilterSection> =
            LEGACY_SECTIONS[value] ?: listOfNotNull(FilterSection.entries.firstOrNull { it.storageValue == value })

        private fun quickOf(value: String): QuickFacet? =
            QuickFacet.entries.firstOrNull { it.storageValue == (LEGACY_QUICK[value] ?: value) }

        private fun String.values(): List<String> = split(",").filter(String::isNotEmpty).distinct()

        private const val PART = "|"

        /** The earlier panel's sections, each as the sections that now hold its choices. */
        private val LEGACY_SECTIONS =
            mapOf(
                "show" to listOf(FilterSection.Status, FilterSection.Quick),
                "from" to listOf(FilterSection.People),
                "sent_to" to listOf(FilterSection.People),
                "received_as" to listOf(FilterSection.People),
                "accounts" to listOf(FilterSection.Rows),
                "in_folder" to listOf(FilterSection.Rows),
                "skip_folders" to listOf(FilterSection.Rows),
                "date" to listOf(FilterSection.Rows),
                "size" to listOf(FilterSection.Rows),
            )

        /**
         * The earlier Show choices: renamed ones point at their new value; those that moved to the
         * Status toggle or the mail type chips, or were removed, point at nothing.
         */
        private val LEGACY_QUICK =
            mapOf(
                "waiting" to QuickFacet.AwaitingReply.storageValue,
                "unread" to "",
                "read" to "",
                "not_flagged" to "",
                "replied" to "",
                "not_replied" to "",
                "newsletters" to "",
                "invites" to "",
            )

        /** Quick filters that replaced no earlier choice, so an earlier layout shows them too. */
        private val ADDED_QUICK = listOf(QuickFacet.NeedsMyReply)
    }
}
