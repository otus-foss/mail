package com.otusfoss.mail.feature.inbox.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import com.otusfoss.mail.core.messages.presentation.ListNudge
import com.otusfoss.mail.feature.inbox.presentation.components.CollapseLink

/**
 * How a message list sits on its page: [look] draws its rows, [nudge] sits above the first one,
 * [topInset] keeps that row below the control panel drawn over the list, [bottomInset] lets the
 * last row scroll above the action bar, [collapse] collapses the panel as the list scrolls down,
 * and [onLoadMore] loads the next page near the end.
 */
internal data class ListFrame(
    val look: RowLook,
    val nudge: ListNudge?,
    val topInset: Dp,
    val bottomInset: Dp,
    val collapse: CollapseLink,
    val onLoadMore: () -> Unit,
    /** Whether filters narrow the list, so its rows keep the address badges the whole list had. */
    val narrowed: Boolean = false,
)

/** This page's list frame; a search hides the nudge, which is about the page's own mail. */
internal fun InboxContext.listFrame(
    searching: Boolean,
    topInset: Dp,
    barPadding: PaddingValues,
    collapse: CollapseLink,
): ListFrame =
    ListFrame(
        rowLook,
        nudge.takeIf { !searching },
        topInset,
        barPadding.calculateBottomPadding(),
        collapse,
        onLoadMore,
    )
