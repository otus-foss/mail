package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.ContextPanelScope
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.has
import com.otus.mail.feature.inbox.domain.with
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxPills
import org.jetbrains.compose.resources.DrawableResource

/** The filter as its pill and the settings list name it. */
internal val InboxPill.Filter.label: String
    get() =
        when (this) {
            InboxPill.Filter.Unread -> "Unread"
            InboxPill.Filter.Attachments -> "Attachments"
            InboxPill.Filter.Flagged -> "Flagged"
        }

internal val InboxPill.Filter.icon: DrawableResource
    get() =
        when (this) {
            InboxPill.Filter.Unread -> Res.drawable.symbol_mark_email_unread
            InboxPill.Filter.Attachments -> Res.drawable.symbol_attach_file
            InboxPill.Filter.Flagged -> Res.drawable.symbol_flag
        }

/**
 * The row outside search, in the user's order. A filter pill turns its filter on and off; a folder
 * pill shows its folders' mail, and pressing it again returns to the page's own.
 */
@Composable
internal fun ContextPanelScope.InboxRowPills(
    pills: InboxPills,
    search: InboxSearch,
    folderPill: InboxPill.FolderPill?,
    onChange: (InboxSearch) -> Unit,
    onFolder: (InboxPill.FolderPill?) -> Unit,
) {
    pills.pills.forEach { pill ->
        when (pill) {
            is InboxPill.Filter -> {
                item(pill) {
                    val on = search.has(pill)
                    Chip(pill.label, { onChange(search.with(pill, !on)) }, selected = on) { Symbol(pill.icon) }
                }
            }

            is InboxPill.FolderPill -> {
                pills.option(pill)?.let { option ->
                    item(pill) { FolderPillChip(option, selected = pill == folderPill, onFolder) }
                }
            }
        }
    }
}

@Composable
internal fun FolderPillChip(
    option: FolderPillOption,
    selected: Boolean,
    onFolder: (InboxPill.FolderPill?) -> Unit,
) {
    Chip(option.label, { onFolder(option.pill.takeUnless { selected }) }, selected = selected) {
        Symbol(option.icon)
    }
}
