package com.otusfoss.mail.core.messages.domain

import kotlinx.datetime.LocalDate

/** Whether the list keeps all mail, only unread mail, or only read mail. */
enum class ReadStatus { All, Unread, Read }

/**
 * An on/off choice of the Quick filters. [clientOnly] choices have no `Email/query` condition, so
 * the source checks them on the loaded threads and cannot count them.
 */
enum class QuickFacet(
    val storageValue: String,
    val clientOnly: Boolean = false,
) {
    Flagged("flagged"),
    ReadLater("read_later"),
    Attachments("attachments"),

    /** The newest email of the thread is someone else's, and the user took part or was in its To. */
    NeedsMyReply("needs_my_reply", clientOnly = true),

    /** The newest email of the thread is the user's. */
    AwaitingReply("awaiting_reply", clientOnly = true),

    /** Threads where the user and someone else both wrote. */
    Conversations("conversations", clientOnly = true),
    Forwarded("forwarded"),
}

/** A kind of mail the Mail filters keep. Calendar invites have no query condition, like [QuickFacet.clientOnly]. */
enum class TypeFacet(
    val clientOnly: Boolean = false,
) {
    Newsletters,
    CalendarInvites(clientOnly = true),
}

/** Where an address appears on mail: the To, Cc, or Bcc field. */
enum class RecipientField(
    val jmapName: String,
) {
    To("to"),
    Cc("cc"),
    Bcc("bcc"),
    ;

    companion object {
        /** The fields "Received in" starts with: mail sent to or copied to the address. */
        val Default: Set<RecipientField> = setOf(To, Cc)
    }
}

/** How large the mail is at least. */
enum class SizeFilter(
    val minBytes: Long?,
) {
    Any(null),
    Over1Mb(ONE_MEGABYTE),
    Over5Mb(FIVE_MEGABYTES),
    Over10Mb(TEN_MEGABYTES),
}

private const val ONE_MEGABYTE = 1_000_000L
private const val FIVE_MEGABYTES = 5_000_000L
private const val TEN_MEGABYTES = 10_000_000L

/** One of the user's own addresses: [accountId] is the login that receives mail at it. */
data class OwnAddress(
    val accountId: String,
    val address: String,
)

/**
 * A person the From line picks: one display [name] and every address they write from, such as
 * two `noreply@` addresses of one company. Picking it keeps mail from any of [addresses].
 */
data class SenderKey(
    val name: String,
    val addresses: Set<String>,
) {
    /** The name, or the first address while the name is blank. */
    val label: String
        get() = name.ifBlank { addresses.sorted().firstOrNull().orEmpty() }

    companion object {
        /** A sender known only by [address]. */
        fun of(address: String): SenderKey = SenderKey("", setOf(address))
    }
}

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

    /** Since 1 January of the current year. */
    data object ThisYear : DateFilter

    /** From the start of [from] to the end of [to]. */
    data class Range(
        val from: LocalDate,
        val to: LocalDate,
    ) : DateFilter
}

enum class FilterSort { NewestFirst, OldestFirst }
