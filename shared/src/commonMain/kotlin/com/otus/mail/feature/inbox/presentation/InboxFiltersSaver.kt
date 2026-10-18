package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.ShowFacet
import kotlinx.datetime.LocalDate

/**
 * Keeps applied filters through activity recreation, as one list of lines: the sort, the date,
 * then one line per choice. Lines this version cannot read are left out.
 */
val InboxFiltersSaver: Saver<InboxFilters, Any> =
    listSaver(
        save = { filters ->
            buildList {
                add(filters.sort.name)
                add(filters.date.saved())
                filters.show.forEach { add("$SHOW${it.name}") }
                filters.sentTo.forEach { add(listOf(SENT_TO, it.accountId, it.address).joinToString(SEPARATOR)) }
                filters.folders.forEach { add(it.saved()) }
            }
        },
        restore = { lines ->
            val parts = lines.drop(2).map { it.split(SEPARATOR) }
            InboxFilters(
                show = lines.mapNotNull { line -> ShowFacet.entries.firstOrNull { "$SHOW${it.name}" == line } }.toSet(),
                sentTo = parts.filter { it.size == 3 && it[0] == SENT_TO }.map { OwnAddress(it[1], it[2]) }.toSet(),
                folders = parts.filter { it.size == 4 && it[0] == FOLDER }.map(::folderOf).toSet(),
                date = dateOf(lines.getOrNull(1)),
                sort = FilterSort.entries.firstOrNull { it.name == lines.firstOrNull() } ?: FilterSort.NewestFirst,
            )
        },
    )

private fun FolderRef.saved(): String =
    listOf(FOLDER, loginId, mailboxId, includeSubfolders.toString()).joinToString(SEPARATOR)

private fun folderOf(parts: List<String>): FolderRef = FolderRef(parts[1], parts[2], parts[3].toBoolean())

private fun DateFilter.saved(): String =
    when (this) {
        DateFilter.Any -> "any"
        DateFilter.Today -> "today"
        DateFilter.Last7 -> "last7"
        DateFilter.Last30 -> "last30"
        is DateFilter.Range -> "$from$SEPARATOR$to"
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
private const val SHOW = "show:"
private const val SENT_TO = "to"
private const val FOLDER = "folder"
