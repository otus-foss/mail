package com.otusfoss.mail.core.messages.domain

/**
 * A filter of the message list that a bar button turns on and off: only unread mail, a quick
 * filter, or a kind of mail. [storageValue] names it in a saved bar.
 */
sealed interface ListFilter {
    val storageValue: String

    data object Unread : ListFilter {
        override val storageValue = "unread"
    }

    data class Quick(
        val facet: QuickFacet,
    ) : ListFilter {
        override val storageValue: String
            get() = facet.storageValue
    }

    data class Type(
        val facet: TypeFacet,
    ) : ListFilter {
        override val storageValue: String
            get() =
                when (facet) {
                    TypeFacet.Newsletters -> "newsletters"
                    TypeFacet.CalendarInvites -> "calendar_invites"
                }
    }

    companion object {
        /** Every filter a button can hold, in the filter panel's order. */
        val all: List<ListFilter>
            get() = listOf(Unread) + QuickFacet.entries.map(::Quick) + TypeFacet.entries.map(::Type)

        fun fromStorageValue(value: String): ListFilter? = all.firstOrNull { it.storageValue == value }
    }
}
