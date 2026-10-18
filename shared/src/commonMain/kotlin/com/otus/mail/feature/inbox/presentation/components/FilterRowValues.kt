package com.otus.mail.feature.inbox.presentation.components

import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FolderLabel
import com.otus.mail.feature.inbox.presentation.key

/** What a row of the main page shows at its end, whether the user [set] it, and how it is [spoken]. */
internal data class RowValue(
    val text: String,
    val set: Boolean,
    val spoken: String = text,
)

/** "All", or the chosen account's name. */
internal fun accountValue(
    draft: InboxFilters,
    options: FilterOptions,
): RowValue =
    draft.account?.let { id -> RowValue(options.account(id)?.name ?: id, set = true) } ?: RowValue("All", set = false)

/** "Any", or the first chosen folder and how many others, such as "Clients +4". */
internal fun foldersValue(
    draft: InboxFilters,
    options: FilterOptions,
): RowValue {
    val first = draft.folders.firstOrNull() ?: return RowValue("Any", set = false)
    val name = options.folderLabel(first).name
    val others = draft.folders.size - 1
    return if (others == 0) RowValue(name, set = true) else RowValue("$name +$others", true, "$name and $others more")
}

/** The preset's name, or a custom range in the user's date format. */
internal fun dateValue(
    date: DateFilter,
    dateFormatter: DateFormatter,
): String = (date as? DateFilter.Range)?.let { rangeLabel(it, dateFormatter) } ?: date.label

/** The chosen types and size threshold, such as "Newsletters · 5 MB+". */
internal fun metadataValue(draft: InboxFilters): RowValue {
    val types =
        draft.types
            .sortedBy { it.ordinal }
            .joinToString(", ") { it.label }
            .ifEmpty { "Any type" }
    val size = if (draft.size == SizeFilter.Any) "any size" else draft.size.label
    return RowValue("$types · $size", draft.types.isNotEmpty() || draft.size != SizeFilter.Any, "$types, $size")
}

/** A chosen folder's label, telling it apart from folders of the same name. */
internal fun FilterOptions.folderLabel(ref: FolderRef): FolderLabel =
    labels[ref.key] ?: FolderLabel(folder(ref.key)?.name ?: "Folder")
