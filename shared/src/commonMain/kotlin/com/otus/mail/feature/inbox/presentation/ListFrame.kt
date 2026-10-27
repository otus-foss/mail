package com.otus.mail.feature.inbox.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp

/**
 * How a message list sits on its page: [look] draws its rows, [nudge] sits above the first one,
 * [topInset] keeps that row below the control panel drawn over the list, [bottomInset] lets the
 * last row scroll above the action bar, [onCompactChange] shrinks the panel while the list
 * scrolls down, and [onLoadMore] loads the next page near the end.
 */
internal data class ListFrame(
    val look: RowLook,
    val nudge: ListNudge?,
    val topInset: Dp,
    val bottomInset: Dp,
    val onCompactChange: (Boolean) -> Unit,
    val onLoadMore: () -> Unit,
    /** Whether filters narrow the list, so its rows keep the address badges the whole list had. */
    val narrowed: Boolean = false,
)

/** This page's list frame; a search hides the nudge, which is about the page's own mail. */
internal fun InboxContext.listFrame(
    searching: Boolean,
    topInset: Dp,
    barPadding: PaddingValues,
    onCompactChange: (Boolean) -> Unit,
): ListFrame =
    ListFrame(
        rowLook,
        nudge.takeIf {
            !searching
        },
        topInset,
        barPadding.calculateBottomPadding(),
        onCompactChange,
        onLoadMore,
    )
