package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_label
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.designsystem.resources.symbol_newspaper
import com.otus.designsystem.resources.symbol_notifications
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.CountBadge
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.panels.components.ContextPanelScope
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.ReadStatus
import com.otus.mail.feature.inbox.domain.category.MailCategory
import com.otus.mail.feature.inbox.presentation.CategoryTabs
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.label
import org.jetbrains.compose.resources.DrawableResource

/** The filter as its pill and the settings list name it. */
internal val InboxPill.Filter.label: String
    get() =
        when (this) {
            InboxPill.Filter.Unread -> "Unread"
            InboxPill.Filter.Attachments -> "Attachments"
            InboxPill.Filter.Flagged -> "Flagged"
            InboxPill.Filter.Conversations -> "Conversations"
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
 * The row outside search: the [categories] pills first, while they are on, then the page's active
 * filters, drawn as the search pills are; pressing an on/off one removes it, and pressing one with
 * choices opens its filter page. Then the user's pills in their order. A filter pill turns its
 * filter on, which moves it to the front; a folder pill shows its folders' mail, and pressing it
 * again returns to the page's own. The Unread pill shows [unreadCount] in a circle in place of its
 * icon while there is unread mail, on or off.
 */
@Composable
internal fun ContextPanelScope.InboxRowPills(
    pills: InboxPills,
    filters: ActiveFilters,
    folderPill: InboxPill.FolderPill?,
    onFolder: (InboxPill.FolderPill?) -> Unit,
    unreadCount: Int = 0,
    categories: CategoryTabs? = null,
) {
    if (categories != null) CategoryPills(categories)
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

/**
 * One pill per shown category, Primary first, the selected one on. A pill with unread mail shows
 * the count in place of its icon.
 */
@Composable
private fun ContextPanelScope.CategoryPills(tabs: CategoryTabs) {
    tabs.settings.tabs.forEach { category ->
        item(category) {
            val count = tabs.unread[category]?.takeIf { it > 0 }?.badgeText()
            Chip(category.label, { tabs.press(category) }, selected = category == tabs.selected) {
                if (count != null) CountBadge(count) else Symbol(category.icon)
            }
        }
    }
}

private val MailCategory.icon: DrawableResource
    get() =
        when (this) {
            MailCategory.Primary -> Res.drawable.symbol_inbox
            MailCategory.Transactions -> Res.drawable.symbol_label
            MailCategory.Notifications -> Res.drawable.symbol_notifications
            MailCategory.Newsletters -> Res.drawable.symbol_newspaper
        }

/** Counts above 99 read "99+", so the badge stays small. */
private fun Int.badgeText(): String = if (this > MAX_BADGE) "$MAX_BADGE+" else toString()

private const val MAX_BADGE = 99
