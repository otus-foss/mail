package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.CountBadge
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.panels.components.ContextPanelScope
import com.otus.mail.core.messages.domain.ReadStatus
import com.otus.mail.core.messages.presentation.icon
import com.otus.mail.core.messages.presentation.label
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.core.settings.presentation.FolderPillOption
import com.otus.mail.core.settings.presentation.InboxPills
import com.otus.mail.core.settings.presentation.icon
import com.otus.mail.core.settings.presentation.label

/**
 * The row outside search: the page's active filters first, drawn as the search pills are; pressing
 * an on/off one removes it, and pressing one with choices opens its filter page. Then the user's
 * pills in their order. A filter pill turns its filter on, which moves it to the front; a folder
 * pill shows its folders' mail, and pressing it again returns to the page's own. The Unread pill
 * shows [unreadCount] in a circle in place of its icon while there is unread mail, on or off.
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
            val onClick = facet.tap
            val count = unreadBadge.takeIf { facet.key == ReadStatus.Unread }
            if (count != null) {
                Chip(ReadStatus.Unread.label, onClick, selected = true) { CountBadge(count) }
            } else {
                facet.pill(onClick)
            }
        }
    }
    pills.pills.forEach { pill ->
        when (pill) {
            is InboxPill.Filter -> FilterPill(pill, filters, unreadBadge)
            is InboxPill.FolderPill -> FolderPill(pills, pill, folderPill, onFolder)
        }
    }
}

@Composable
private fun ContextPanelScope.FilterPill(
    pill: InboxPill.Filter,
    filters: ActiveFilters,
    unreadBadge: String?,
) {
    if (filters.applied.has(pill)) return
    item(pill) {
        val count = unreadBadge.takeIf { pill == InboxPill.Filter.Unread }
        Chip(pill.label, { filters.onChange(filters.applied.with(pill, on = true)) }) {
            if (count != null) CountBadge(count) else Symbol(pill.icon)
        }
    }
}

@Composable
private fun ContextPanelScope.FolderPill(
    pills: InboxPills,
    pill: InboxPill.FolderPill,
    selected: InboxPill.FolderPill?,
    onFolder: (InboxPill.FolderPill?) -> Unit,
) {
    val option = pills.option(pill) ?: return
    item(pill) { FolderPillChip(option, selected = pill == selected, onFolder) }
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
