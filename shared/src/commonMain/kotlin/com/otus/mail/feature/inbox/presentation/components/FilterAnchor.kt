package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.otus.designsystem.theme.FilterTokens
import com.otus.designsystem.theme.InboxTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.InboxFilterState

/**
 * Ties the filter panel to the control panel it grows from: [measure] the control panel to give
 * the panel its starting height, and [filters] for the pill row, whose button gets focus back
 * when the panel closes. A pill with choices opens the panel on its page.
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
            onOpen = { page ->
                state.open()
                state.openPage(page)
            },
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
) {
    InboxFilterPanel(
        state,
        anchor.filters.options,
        messages,
        dateFormatter,
        anchor.height(),
        Modifier
            .safeDrawingPadding()
            .imePadding()
            .padding(start = InboxTokens.pageInset, end = InboxTokens.pageInset, top = InboxTokens.pageInset)
            .padding(bottom = FilterTokens.screenInset),
    )
}
