package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.otus.designsystem.theme.OtusPalette
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFilterState
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
                InboxScreen(
                    FilterPanelFixtures.messages,
                    context = InboxContext(filters = filters, filterOptions = options),
                )
            }
        }
    }
    if (open) onNodeWithContentDescription("Open filters", substring = true).performClick()
    waitForIdle()
}

/** Opens a page of the panel from its row or line, named as screen readers hear it, such as "Folders, ". */
internal fun AndroidComposeTestRule<*, MainActivity>.openFilterPage(spokenStart: String) {
    onNodeWithContentDescription(spokenStart, substring = true).performScrollTo().performClick()
    waitForIdle()
}

internal fun AndroidComposeTestRule<*, MainActivity>.filterShot(name: String) =
    onRoot().captureRoboImage("src/test/screenshots/$name.png")
