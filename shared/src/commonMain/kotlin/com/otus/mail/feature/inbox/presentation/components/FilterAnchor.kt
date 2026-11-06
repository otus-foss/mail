package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.pages.components.belowControlPanel
import com.otus.designsystem.theme.tokens.selection.FilterTokens
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.InboxFilterState

/**
 * Ties the filter panel to the control panel it grows from: [measure] the control panel to give
 * the panel its starting height, and [filters] for the pill row, whose button gets focus back
 * when the panel closes.
 */
internal class FilterAnchor(
    val filters: ActiveFilters,
    val height: () -> Dp,
    val measure: Modifier,
)

@Composable
internal fun rememberFilterAnchor(
    state: InboxFilterState,
    options: FilterOptions,
    messages: List<MailMessage>,
    dateFormatter: DateFormatter,
): FilterAnchor {
    var height by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val button = remember { FocusRequester() }
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.isOpen) {
        // The button is missing only while the page is leaving, when focus does not matter.
        if (wasOpen && !state.isOpen) button.runCatching { requestFocus() }
        wasOpen = state.isOpen
    }
    return FilterAnchor(
        ActiveFilters(
            state.applied,
            options,
            dateFormatter,
            state::update,
            button,
            people = rememberFilterPeople(state, options, messages),
        ),
        { height },
        Modifier.onSizeChanged { height = with(density) { it.height.toDp() } },
    )
}

/** The filter panel over the page, where its control panel sits. */
@Composable
internal fun InboxFilterLayer(
    state: InboxFilterState,
    messages: List<MailMessage>,
    dateFormatter: DateFormatter,
    anchor: FilterAnchor,
    title: String,
) {
    InboxFilterPanel(
        state,
        anchor.filters.options,
        messages,
        dateFormatter,
        anchor.height(),
        title,
        Modifier
            .belowControlPanel()
            .padding(bottom = FilterTokens.screenInset),
    )
}
