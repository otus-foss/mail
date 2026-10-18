package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.domain.ReadStatus
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.domain.SenderKey
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.domain.TypeFacet
import kotlinx.datetime.LocalDate

/**
 * Keeps applied filters through activity recreation, as one list of lines: the sort, the date,
 * the size, then one line per choice, its kind first. Lines this version cannot read are left out,
 * and lines an earlier version saved read as the choices that replaced them; see [legacyShow].
 */
val InboxFiltersSaver: Saver<InboxFilters, Any> =
    listSaver(
        save = { filters ->
            buildList {
                add(filters.sort.name)
                add(filters.date.saved())
                add(filters.size.name)
                add(line(STATUS, filters.status.name))
                add(line(FIELDS, *filters.receivedIn.map { it.name }.toTypedArray()))
                filters.quick.forEach { add(line(QUICK, it.name)) }
                filters.types.forEach { add(line(TYPE, it.name)) }
                filters.from.forEach { add(line(SENDER, it.name, *it.addresses.toTypedArray())) }
                filters.account?.let { add(line(ACCOUNT, it)) }
                filters.sentTo.forEach { add(line(SENT_TO, it.accountId, it.address)) }
                filters.to.forEach { add(line(RECIPIENT, it)) }
                filters.folders.forEach { add(it.saved()) }
            }
        },
        restore = ::restored,
    )

private fun restored(lines: List<String>): InboxFilters {
    val parts = lines.drop(HEAD_LINES).map { it.split(SEPARATOR) }

    fun values(kind: String) = parts.filter { it.first() == kind }.map { it.drop(1) }
    val legacy = values(LEGACY_SHOW).mapNotNull { (name) -> legacyShow(name) }
    val legacyFields = values(LEGACY_AS).mapNotNull { (name) -> enumOf<RecipientField>(name) }.toSet()
    val senders = values(SENDER).filter { it.size > 1 }.map { SenderKey(it.first(), it.drop(1).toSet()) }
    return InboxFilters(
        status =
            values(STATUS).firstNotNullOfOrNull { (name) -> enumOf<ReadStatus>(name) }
                ?: legacy.firstNotNullOfOrNull { it as? ReadStatus }
                ?: ReadStatus.All,
        quick =
            (
                values(QUICK).mapNotNull { (name) ->
                    enumOf<QuickFacet>(name)
                } + legacy.filterIsInstance<QuickFacet>()
            ).toSet(),
        types =
            (
                values(TYPE).mapNotNull { (name) ->
                    enumOf<TypeFacet>(name)
                } + legacy.filterIsInstance<TypeFacet>()
            ).toSet(),
        from = (senders + values(LEGACY_FROM).map { (address) -> SenderKey.of(address) }).toSet(),
        sentTo = values(SENT_TO).filter { it.size == 2 }.map { (id, address) -> OwnAddress(id, address) }.toSet(),
        to = values(RECIPIENT).map { it.single() }.toSet(),
        receivedIn =
            values(FIELDS).firstOrNull()?.mapNotNull { enumOf<RecipientField>(it) }?.toSet()
                ?: legacyFields.ifEmpty { RecipientField.Default },
        account = values(ACCOUNT).firstOrNull()?.single(),
        folders = values(FOLDER).mapNotNull(::folderOf).toSet(),
        date = dateOf(lines.getOrNull(1)),
        size = enumOf<SizeFilter>(lines.getOrNull(2)) ?: SizeFilter.Any,
        sort = enumOf<FilterSort>(lines.firstOrNull()) ?: FilterSort.NewestFirst,
    )
}

/**
 * A Show choice an earlier version saved, as the choice that replaced it: Unread and Read are the
 * status, Waiting for reply is Awaiting reply, Newsletters and Calendar invites are types, and
 * the removed Not flagged, Replied, and Not replied are nothing.
 */
private fun legacyShow(name: String): Any? =
    when (name) {
        "Unread" -> ReadStatus.Unread
        "Read" -> ReadStatus.Read
        "WaitingForReply" -> QuickFacet.AwaitingReply
        "Newsletters" -> TypeFacet.Newsletters
        "CalendarInvites" -> TypeFacet.CalendarInvites
        else -> enumOf<QuickFacet>(name)
    }

private inline fun <reified T : Enum<T>> enumOf(name: String?): T? = enumValues<T>().firstOrNull { it.name == name }

private fun line(vararg values: String): String = values.joinToString(SEPARATOR)

private fun FolderRef.saved(): String = line(FOLDER, loginId, mailboxId, includeSubfolders.toString())

private fun folderOf(values: List<String>): FolderRef? =
    values.takeIf { it.size == FOLDER_PARTS }?.let { (login, id, include) -> FolderRef(login, id, include.toBoolean()) }

private fun DateFilter.saved(): String =
    when (this) {
        DateFilter.Any -> "any"
        DateFilter.Today -> "today"
        DateFilter.Last7 -> "last7"
        DateFilter.Last30 -> "last30"
        DateFilter.ThisYear -> "year"
        is DateFilter.Range -> line(from.toString(), to.toString())
    }

private fun dateOf(line: String?): DateFilter =
    when (line) {
        "today" -> {
            DateFilter.Today
        }

        "last7" -> {
            DateFilter.Last7
        }

        "last30" -> {
            DateFilter.Last30
        }

        "year" -> {
            DateFilter.ThisYear
        }

        else -> {
            val ends = line?.split(SEPARATOR)?.takeIf { it.size == 2 }
            ends?.let { (from, to) -> DateFilter.Range(LocalDate.parse(from), LocalDate.parse(to)) } ?: DateFilter.Any
        }
    }

// Server ids and addresses never contain a NUL.
private const val SEPARATOR = "\u0000"
private const val HEAD_LINES = 3
private const val FOLDER_PARTS = 3
private const val STATUS = "status"
private const val QUICK = "quick"
private const val TYPE = "type"
private const val SENDER = "sender"
private const val ACCOUNT = "account"
private const val SENT_TO = "to"
private const val RECIPIENT = "recipient"
private const val FIELDS = "fields"
private const val FOLDER = "folder"

// Kinds an earlier version saved: Show choices, bare sender addresses, and the Received as fields.
private const val LEGACY_SHOW = "show"
private const val LEGACY_FROM = "from"
private const val LEGACY_AS = "as"
