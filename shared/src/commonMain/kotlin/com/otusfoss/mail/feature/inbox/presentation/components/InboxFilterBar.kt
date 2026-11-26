package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.shared.filters.components.FilterShowBar
import com.otusfoss.mail.feature.inbox.presentation.FilterCount
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.inbox.presentation.showLabel

/** [state]'s Show and Reset in an action bar of their own; Show runs [onApply]. */
@Composable
internal fun InboxFilterBar(
    state: InboxFilterState,
    onApply: () -> Unit,
) {
    // The last count stays while the next one is counted, so the label rolls from one to the other.
    var count by remember { mutableStateOf(state.count) }
    state.count.takeIf { it != FilterCount.Counting }?.let { count = it }
    FilterShowBar(
        count.showLabel,
        onApply,
        state::reset,
        applyEnabled = count != FilterCount.Known(0),
        resetEnabled = state.draft?.isDefault == false,
    )
}
