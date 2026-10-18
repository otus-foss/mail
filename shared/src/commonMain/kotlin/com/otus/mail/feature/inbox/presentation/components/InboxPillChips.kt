package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.ContextPanelScope
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.elements.CountBadge
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.ShowFacet
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
            InboxPill.Filter.Conversations -> "Convos"
        }

internal val InboxPill.Filter.icon: DrawableResource
    get() =
        when (this) {
            InboxPill.Filter.Unread -> Res.drawable.symbol_mark_email_unread
            InboxPill.Filter.Attachments -> Res.drawable.symbol_attach_file
            InboxPill.Filter.Flagged -> Res.drawable.symbol_flag
            InboxPill.Filter.Conversations -> Res.drawable.symbol_forum
        }

/**
 * The row outside search: the page's active filters first, drawn as the search pills are, and
 * pressing one removes it; then the user's pills in their order. A filter pill turns its filter on,
 * which moves it to the front; a folder pill shows its folders' mail, and pressing it again returns
 * to the page's own. The Unread pill shows [unreadCount] in a circle in place of its icon while
 * there is unread mail, on or off.
 */
@Composable
internal fun ContextPanelScope.InboxRowPills(
    pills: InboxPills,
    filters: ActiveFilters,
    folderPill: InboxPill.FolderPill?,
    onFolder: (InboxPill.FolderPill?) -> Unit,
    unreadCount: Int = 0,
) {
    val unreadBadge = unreadCount.takeIf { it > 0 }?.badgeText()
    filters.active.forEach { facet ->
        item(facet.key) {
            val onClick = { filters.onChange(facet.remove(filters.applied)) }
            val count = unreadBadge.takeIf { facet.key == ShowFacet.Unread }
            if (count != null) {
                Chip(ShowFacet.Unread.label, onClick, selected = true) { CountBadge(count) }
            } else {
                facet.pill(onClick)
            }
        }
    }
    pills.pills.forEach { pill ->
        when (pill) {
            is InboxPill.Filter -> {
                if (!filters.applied.has(pill)) {
                    item(pill) {
                        val count = unreadBadge.takeIf { pill == InboxPill.Filter.Unread }
                        Chip(pill.label, { filters.onChange(filters.applied.with(pill, true)) }) {
                            if (count != null) CountBadge(count) else Symbol(pill.icon)
                        }
                    }
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

/** Counts above 99 read "99+", so the badge stays small. */
private fun Int.badgeText(): String = if (this > MAX_BADGE) "$MAX_BADGE+" else toString()

private const val MAX_BADGE = 99
