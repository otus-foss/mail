package com.otusfoss.mail.feature.inbox.presentation.components

import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.mail.core.messages.domain.DateFilter
import com.otusfoss.mail.core.messages.domain.FolderRef
import com.otusfoss.mail.core.messages.presentation.label
import com.otusfoss.mail.core.messages.presentation.rangeLabel
import com.otusfoss.mail.core.settings.presentation.label
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.presentation.FilterOptions
import com.otusfoss.mail.feature.inbox.presentation.FolderLabel
import com.otusfoss.mail.feature.inbox.presentation.key

/** What a row of the main page shows at its end, whether the user [set] it, and how it is [spoken]. */
internal data class FilterRowValue(
    val text: String,
    val set: Boolean,
    val spoken: String = text,
)

/** "All", or the chosen account's name. */
internal fun accountValue(
    draft: InboxFilters,
    options: FilterOptions,
): FilterRowValue =
    draft.account?.let { id -> FilterRowValue(options.account(id)?.name ?: id, set = true) }
        ?: FilterRowValue("All", set = false)

/** "Any", or the first chosen folder and how many others, such as "Clients +4". */
internal fun foldersValue(
    draft: InboxFilters,
    options: FilterOptions,
): FilterRowValue {
    val first = draft.folders.firstOrNull() ?: return FilterRowValue("Any", set = false)
    val name = options.folderLabel(first).name
    val others = draft.folders.size - 1
    return when (others) {
        0 -> FilterRowValue(name, set = true)
        else -> FilterRowValue("$name +$others", set = true, spoken = "$name and $others more")
    }
}

/** The preset's name, or a custom range in the user's date format. */
internal fun dateValue(
    date: DateFilter,
    dateFormatter: DateFormatter,
): String = (date as? DateFilter.Range)?.let { rangeLabel(it, dateFormatter) } ?: date.label

/** A chosen folder's label, telling it apart from folders of the same name. */
internal fun FilterOptions.folderLabel(ref: FolderRef): FolderLabel =
    labels[ref.key] ?: FolderLabel(folder(ref.key)?.name ?: "Folder")
