package com.otusfoss.mail.feature.inbox.presentation.components

import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.mail.core.messages.domain.DateFilter
import com.otusfoss.mail.core.messages.domain.FolderRef
import com.otusfoss.mail.core.messages.presentation.label
import com.otusfoss.mail.core.messages.presentation.rangeLabel
import com.otusfoss.mail.core.settings.presentation.label
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.domain.withDefaultFolders
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

/**
 * "Any", the folders a search starts from among [defaultFolders], "All folders" once a search
 * leaves them, or the chosen folders, such as "Clients +4". Added Spam and Trash follow "Any".
 */
internal fun foldersValue(
    draft: InboxFilters,
    options: FilterOptions,
    defaultFolders: Set<FolderRef> = emptySet(),
): FilterRowValue {
    val changed = draft.hasFolderOverride
    val folders = draft.withDefaultFolders(defaultFolders).folders
    if (folders.isEmpty()) {
        val text =
            when {
                draft.includedBins.isNotEmpty() -> "Any + ${draft.binNames}"
                draft.allFolders -> "All folders"
                else -> "Any"
            }
        return FilterRowValue(text, set = changed)
    }
    val names = folders.map { options.folderLabel(it).name }
    val others = folders.size - 1
    return when {
        others == 0 -> FilterRowValue(names.first(), set = changed)
        !changed && names.distinct().size == 1 -> FilterRowValue(names.first(), set = false)
        else -> FilterRowValue("${names.first()} +$others", set = changed, spoken = "${names.first()} and $others more")
    }
}

/** The added Spam and Trash, such as "Spam, Trash". */
internal val InboxFilters.binNames: String
    get() = includedBins.sortedBy { it.ordinal }.joinToString(", ") { it.title }

/** The preset's name, or a custom range in the user's date format. */
internal fun dateValue(
    date: DateFilter,
    dateFormatter: DateFormatter,
): String = (date as? DateFilter.Range)?.let { rangeLabel(it, dateFormatter) } ?: date.label

/** A chosen folder's label, telling it apart from folders of the same name. */
internal fun FilterOptions.folderLabel(ref: FolderRef): FolderLabel =
    labels[ref.key] ?: FolderLabel(folder(ref.key)?.name ?: "Folder")
