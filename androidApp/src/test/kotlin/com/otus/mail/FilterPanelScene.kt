package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.colors.OtusPalette
import com.otus.mail.core.text.TYPING_PAUSE_MILLIS
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.components.LocalToday
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen

/**
 * The inbox of [FilterPanelFixtures] with [applied] filters in [palette], the filter panel open
 * while [open], counting [total] conversations.
 */
internal fun AndroidComposeTestRule<*, MainActivity>.showFilterPanel(
    applied: InboxFilters = InboxFilters(),
    open: Boolean = true,
    total: Long? = 12,
    options: FilterOptions = FilterPanelFixtures.options,
    palette: OtusPalette = OtusPalette.Otus,
) {
    runOnUiThread {
        activity.setContent {
            val scope = rememberCoroutineScope()
            val filters = remember { InboxFilterState(applied, scope, counter = { total }) }
            OtusTheme(palette) {
                CompositionLocalProvider(LocalToday provides PinnedToday) {
                    InboxScreen(
                        FilterPanelFixtures.messages,
                        context =
                            inboxContext {
                                this.filters = filters
                                filterOptions = options
                            },
                    )
                }
            }
        }
    }
    if (open) onNodeWithContentDescription("Open filters", substring = true).performClick()
    // The panel counts its draft after the typing pause.
    mainClock.advanceTimeBy(TYPING_PAUSE_MILLIS * 2)
    waitForIdle()
}

/** A segment of the panel's Status, Sort, or Size toggle, such as "Unread". */
internal fun SemanticsNodeInteractionsProvider.filterTab(label: String): SemanticsNodeInteraction =
    onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
