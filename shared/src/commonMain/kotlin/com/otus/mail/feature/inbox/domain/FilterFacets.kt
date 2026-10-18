package com.otus.mail.feature.inbox.domain

import kotlinx.datetime.LocalDate

/**
 * A condition of the Show section; all that are on must hold. Some come in pairs, such as Unread
 * and Read, and turning one on turns its [opposite] off.
 */
enum class ShowFacet(
    val storageValue: String,
) {
    Unread("unread"),
    Read("read"),
    Flagged("flagged"),
    NotFlagged("not_flagged"),
    ReadLater("read_later"),
    Attachments("attachments"),
    Replied("replied"),
    NotReplied("not_replied"),
    Forwarded("forwarded"),
    WaitingForReply("waiting"),
    Newsletters("newsletters"),
    CalendarInvites("invites"),
    ;

    val opposite: ShowFacet?
        get() =
            when (this) {
                Unread -> Read
                Read -> Unread
                Flagged -> NotFlagged
                NotFlagged -> Flagged
                Replied -> NotReplied
                NotReplied -> Replied
                else -> null
            }
}

/** Where one of the user's addresses appears on mail they received. */
enum class RecipientField(
    val jmapName: String,
) {
    To("to"),
    Cc("cc"),
    Bcc("bcc"),
}

/** How large the mail is at least. */
enum class SizeFilter(
    val minBytes: Long?,
) {
    Any(null),
    Over1Mb(MEGABYTE),
    Over5Mb(5 * MEGABYTE),
    Over10Mb(10 * MEGABYTE),
}

private const val MEGABYTE = 1_000_000L

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

/** The Show condition a row pill turns on. */
val InboxPill.Filter.facet: ShowFacet
    get() =
        when (this) {
            InboxPill.Filter.Unread -> ShowFacet.Unread
            InboxPill.Filter.Attachments -> ShowFacet.Attachments
            InboxPill.Filter.Flagged -> ShowFacet.Flagged
        }
