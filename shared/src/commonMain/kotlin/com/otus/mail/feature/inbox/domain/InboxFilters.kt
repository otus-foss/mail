package com.otus.mail.feature.inbox.domain

import kotlinx.datetime.LocalDate

/** A condition of the Show section; all that are on must hold. */
enum class ShowFacet { Unread, Flagged, ReadLater, Attachments, WaitingForReply }

/** One of the user's own addresses: [accountId] is the login that receives mail at it. */
data class OwnAddress(
    val accountId: String,
    val address: String,
)

/** A folder to filter by: [loginId] is the login, [mailboxId] the server's id. */
data class FolderRef(
    val loginId: String,
    val mailboxId: String,
    val includeSubfolders: Boolean = true,
)

/** When the mail arrived, in the user's time zone. */
sealed interface DateFilter {
    data object Any : DateFilter

    data object Today : DateFilter

    data object Last7 : DateFilter

    data object Last30 : DateFilter

    /** From the start of [from] to the end of [to]. */
    data class Range(
        val from: LocalDate,
        val to: LocalDate,
    ) : DateFilter
}

enum class FilterSort { NewestFirst, OldestFirst }

/**
 * What a page's list is narrowed to. The quick pills and the filter panel edit the same value.
 * [show] conditions must all hold; [sentTo] and [folders] each match any of their choices.
 */
data class InboxFilters(
    val show: Set<ShowFacet> = emptySet(),
    val sentTo: Set<OwnAddress> = emptySet(),
    val folders: Set<FolderRef> = emptySet(),
    val date: DateFilter = DateFilter.Any,
    val sort: FilterSort = FilterSort.NewestFirst,
) {
    /** How many choices differ from the defaults, one per pill the row shows; the order is not one. */
    val activeCount: Int
        get() = show.size + sentTo.size + folders.size + (if (date == DateFilter.Any) 0 else 1)

    /** Nothing narrows the list, and it is newest first. */
    val isDefault: Boolean
        get() = this == InboxFilters()

    fun has(facet: ShowFacet): Boolean = facet in show

    /** These filters with [facet] turned [on] or off. */
    fun with(
        facet: ShowFacet,
        on: Boolean,
    ): InboxFilters = copy(show = if (on) show + facet else show - facet)

    fun has(pill: InboxPill.Filter): Boolean = has(pill.facet)

    fun with(
        pill: InboxPill.Filter,
        on: Boolean,
    ): InboxFilters = with(pill.facet, on)

    /** Only [ref]'s folder, a different subfolder choice of the same folder replacing it. */
    fun withFolder(
        ref: FolderRef,
        on: Boolean,
    ): InboxFilters {
        val others = folders.filterNot { it.loginId == ref.loginId && it.mailboxId == ref.mailboxId }.toSet()
        return copy(folders = if (on) others + ref else others)
    }
}

/** The Show condition a row pill turns on. */
val InboxPill.Filter.facet: ShowFacet
    get() =
        when (this) {
            InboxPill.Filter.Unread -> ShowFacet.Unread
            InboxPill.Filter.Attachments -> ShowFacet.Attachments
            InboxPill.Filter.Flagged -> ShowFacet.Flagged
        }

/**
 * [filters] with what the server needs to run them: each login's folder ids with subfolders
 * already added, each login's Read later folder, the user's own [ownAddresses], which tell mail
 * the user sent, and the [logins] the page shows, or null for all.
 */
data class MailQuery(
    val filters: InboxFilters = InboxFilters(),
    val folderIds: Map<String, Set<String>> = emptyMap(),
    val readLaterIds: Map<String, String> = emptyMap(),
    val ownAddresses: Set<String> = emptySet(),
    val logins: Set<String>? = null,
) {
    /** Whether this query reads mail of [loginId] at all; selected addresses and folders leave out other logins. */
    fun reads(loginId: String): Boolean =
        (logins == null || loginId in logins) &&
            (filters.sentTo.isEmpty() || filters.sentTo.any { it.accountId == loginId }) &&
            (filters.folders.isEmpty() || loginId in folderIds) &&
            (ShowFacet.ReadLater !in filters.show || loginId in readLaterIds)
}

/**
 * Each login's folder ids for [refs]: the folder, and with [FolderRef.includeSubfolders] every
 * folder [descendants] names below it.
 */
fun folderIds(
    refs: Set<FolderRef>,
    descendants: (loginId: String, mailboxId: String) -> Set<String>,
): Map<String, Set<String>> =
    refs
        .groupBy { it.loginId }
        .mapValues { (login, own) ->
            own
                .flatMap { ref ->
                    setOf(ref.mailboxId) +
                        if (ref.includeSubfolders) descendants(login, ref.mailboxId) else emptySet()
                }.toSet()
        }

/** Mail the user sent, which waits for a reply while it is the latest of its conversation. */
fun MailMessage.fromAny(addresses: Set<String>): Boolean =
    addresses.any { it.equals(sender.address, ignoreCase = true) }
