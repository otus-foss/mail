package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otusfoss.mail.core.categories.domain.MailCategory
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.feature.inbox.presentation.CategoryTabs
import com.otusfoss.mail.feature.inbox.presentation.InboxContext
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.inbox.presentation.InboxState
import com.otusfoss.mail.feature.inbox.presentation.InboxStatus
import com.otusfoss.mail.feature.inbox.presentation.unreadCounts

/**
 * The page's mail as its category pills show it: the [tabs], null while the page shows none, the
 * selected pill's [messages], those the applied filters keep, [listed], and [onLoadMore], which
 * pages for the pill; see [rememberCategoryPaging].
 */
internal class CategoryView(
    val tabs: CategoryTabs?,
    val messages: List<MailMessage>,
    val listed: ListedMail,
    val onLoadMore: () -> Unit,
)

/**
 * The pills of [page]'s categories over [messages], or all of [messages] on a page without them.
 * They sort only the page's own mail, so search and a folder pill show all of theirs. The selected
 * pill outlives the page; a pill the user hides falls back to Primary.
 */
@Composable
internal fun rememberCategoryView(
    page: InboxContext,
    state: InboxState,
    messages: List<MailMessage>,
    status: InboxStatus,
    filters: InboxFilterState,
): CategoryView {
    val categories = page.categories.takeIf { !state.searching && state.folderPill == null }
    var selected by rememberSaveable { mutableStateOf(MailCategory.Primary.storageValue) }
    val unread = remember(categories, messages) { categories?.unreadCounts(messages).orEmpty() }
    val tab = MailCategory.fromStorageValue(selected)?.takeIf { categories?.shows(it) == true } ?: MailCategory.Primary
    val tabs = categories?.let { CategoryTabs(it, tab, unread) { picked -> selected = picked.storageValue } }
    val shown = remember(messages, categories, tab) { tabs?.shown(messages) ?: messages }
    val paging = rememberCategoryPaging(tabs?.selected, shown.size, status, page.onLoadMore)
    return CategoryView(tabs, shown, ListedMail(shown, filters.applied), paging)
}
