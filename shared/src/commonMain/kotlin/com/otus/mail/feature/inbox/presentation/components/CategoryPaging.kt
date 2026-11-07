package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.otus.mail.core.categories.domain.MailCategory
import com.otus.mail.feature.inbox.presentation.InboxStatus

/**
 * How the list loads older pages while the [category] pill shows [shown] rows. A pill shows only
 * its share of the loaded mail, so a page can add nothing to it. See [CategoryPaging]. An empty
 * pill has no end to scroll to, so it loads by itself. Without a pill every load goes through.
 */
@Composable
internal fun rememberCategoryPaging(
    category: MailCategory?,
    shown: Int,
    status: InboxStatus,
    onLoadMore: () -> Unit,
): () -> Unit {
    val paging = remember(category) { CategoryPaging() }
    val current by rememberUpdatedState(shown)
    LaunchedEffect(paging, status.loadingMore) { if (!status.loadingMore) paging.settle(current) }
    LaunchedEffect(paging, shown, status) {
        val idle = status.hasMore && !status.loading && !status.loadingMore
        if (category != null && shown == 0 && idle) paging.load(current, onLoadMore)
    }
    return if (category == null) onLoadMore else ({ paging.load(current, onLoadMore) })
}

/**
 * Counts the pages in a row that added no rows to a category pill, and stops loading after
 * [MAX_IDLE_PAGES] of them, so a quiet category does not page through the whole mailbox. A page
 * that adds rows starts the count again, and so does choosing another pill.
 */
internal class CategoryPaging {
    private var idle = 0
    private var loadedFrom: Int? = null

    /** Loads the next page with [onLoadMore], unless too many pages in a row added nothing. */
    fun load(
        shown: Int,
        onLoadMore: () -> Unit,
    ) {
        if (idle >= MAX_IDLE_PAGES) return
        loadedFrom = shown
        onLoadMore()
    }

    /** A load ended with [shown] rows in the pill. */
    fun settle(shown: Int) {
        val start = loadedFrom ?: return
        loadedFrom = null
        idle = if (shown > start) 0 else idle + 1
    }

    private companion object {
        const val MAX_IDLE_PAGES = 3
    }
}
