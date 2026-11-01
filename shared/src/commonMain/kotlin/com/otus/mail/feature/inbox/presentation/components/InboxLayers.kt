package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxState

/**
 * The [overlays] over the inbox of [listed] mail, each grown out of the control panel's place that
 * [anchor] measures on [page]. A search hit opens through [rows], as a row in the list would.
 */
@Composable
internal fun InboxLayers(
    state: InboxState,
    listed: ListedMail,
    anchor: FilterAnchor,
    page: InboxContext,
    dates: HitDates,
    overlays: InboxOverlays,
    rows: InboxRows,
) {
    InboxFilterLayer(overlays.filters, listed.messages, dates.formatter, anchor, page.title)
    page.scope?.let {
        InboxAccountSwitcher(it, page.title, overlays.accountsOpen, anchor.height()) { overlays.accountsOpen = false }
    }
    InboxQuickView(overlays, page, listed.messages, anchor.height(), { rows.gestures.onOpen(it) }, state::startSearch)
    InboxSearchLayer(state, listed, anchor.height(), dates) { rows.gestures.onOpen(it) }
    overlays.filter?.let { selected ->
        InboxFilterSheet(selected, listed.messages, state.search, page, dates.formatter, state::update) {
            overlays.filter = null
        }
    }
}
