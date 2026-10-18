package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.domain.ShowFacet
import com.otus.mail.feature.inbox.domain.SizeFilter
import kotlinx.datetime.LocalDate

/**
 * Keeps applied filters through activity recreation, as one list of lines: the sort, the date,
 * the size, then one line per choice, its kind first. Lines this version cannot read are left out.
 */
val InboxFiltersSaver: Saver<InboxFilters, Any> =
    listSaver(
        save = { filters ->
            buildList {
                add(filters.sort.name)
                add(filters.date.saved())
                add(filters.size.name)
                filters.show.forEach { add(line(SHOW, it.name)) }
                filters.from.forEach { add(line(FROM, it)) }
                filters.accounts.forEach { add(line(ACCOUNT, it)) }
                filters.sentTo.forEach { add(line(SENT_TO, it.accountId, it.address)) }
                filters.receivedAs.forEach { add(line(RECEIVED_AS, it.name)) }
                filters.folders.forEach { add(it.saved(FOLDER)) }
                filters.skipFolders.forEach { add(it.saved(SKIP)) }
            }
        },
        restore = { lines ->
            val parts = lines.drop(HEAD_LINES).map { it.split(SEPARATOR) }

            fun values(kind: String) = parts.filter { it.first() == kind }.map { it.drop(1) }
            InboxFilters(
                show = values(SHOW).mapNotNull { (name) -> ShowFacet.entries.firstOrNull { it.name == name } }.toSet(),
                from = values(FROM).map { it.single() }.toSet(),
                accounts = values(ACCOUNT).map { it.single() }.toSet(),
                sentTo =
                    values(SENT_TO)
                        .filter { it.size == 2 }
                        .map { (id, address) ->
                            OwnAddress(id, address)
                        }.toSet(),
                receivedAs =
                    values(RECEIVED_AS)
                        .mapNotNull { (name) ->
                            RecipientField.entries.firstOrNull {
                                it.name ==
                                    name
                            }
                        }.toSet(),
                folders = values(FOLDER).mapNotNull(::folderOf).toSet(),
                skipFolders = values(SKIP).mapNotNull(::folderOf).toSet(),
                date = dateOf(lines.getOrNull(1)),
                size = SizeFilter.entries.firstOrNull { it.name == lines.getOrNull(2) } ?: SizeFilter.Any,
                sort = FilterSort.entries.firstOrNull { it.name == lines.firstOrNull() } ?: FilterSort.NewestFirst,
            )
        },
    )

private fun line(vararg values: String): String = values.joinToString(SEPARATOR)

private fun FolderRef.saved(kind: String): String = line(kind, loginId, mailboxId, includeSubfolders.toString())

private fun folderOf(values: List<String>): FolderRef? =
    values.takeIf { it.size == 3 }?.let { (login, id, include) -> FolderRef(login, id, include.toBoolean()) }

private fun DateFilter.saved(): String =
    when (this) {
        DateFilter.Any -> "any"
        DateFilter.Today -> "today"
        DateFilter.Last7 -> "last7"
        DateFilter.Last30 -> "last30"
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

        else -> {
            val ends = line?.split(SEPARATOR)?.takeIf { it.size == 2 }
            ends?.let { (from, to) -> DateFilter.Range(LocalDate.parse(from), LocalDate.parse(to)) } ?: DateFilter.Any
        }
    }

// Server ids and addresses never contain a NUL.
private const val SEPARATOR = "\u0000"
private const val HEAD_LINES = 3
private const val SHOW = "show"
private const val FROM = "from"
private const val ACCOUNT = "account"
private const val SENT_TO = "to"
private const val RECEIVED_AS = "as"
private const val FOLDER = "folder"
private const val SKIP = "skip"
