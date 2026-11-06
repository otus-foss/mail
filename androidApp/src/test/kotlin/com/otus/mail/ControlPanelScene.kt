package com.otus.mail

import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.takahirom.roborazzi.captureRoboImage
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.colors.OtusPalette
import com.otus.designsystem.theme.colors.ThemeBase
import com.otus.designsystem.theme.colors.ThemeChoice
import com.otus.designsystem.theme.typography.panelTextStyle
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.components.LocalToday
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import com.otus.mail.feature.read.presentation.screens.MessageScreen

/** The status bar of the design's phone, which Robolectric leaves out, so the panel can fill it. */
private const val STATUS_BAR_DP = 40

internal fun palette(base: ThemeBase): OtusPalette = ThemeChoice(base).palette

/** The inbox of [ControlPanelFixtures] in [palette], with its account stack and 33 conversations to show. */
internal fun AndroidComposeTestRule<*, MainActivity>.showControlInbox(palette: OtusPalette = OtusPalette.Otus) {
    runOnUiThread {
        activity.setContent {
            val scope = rememberCoroutineScope()
            val filters = remember { InboxFilterState(scope = scope, counter = { 33 }) }
            OtusTheme(palette) {
                CompositionLocalProvider(LocalToday provides PinnedToday) {
                    InboxScreen(
                        ControlPanelFixtures.messages,
                        context =
                            InboxContext(
                                filters = filters,
                                filterOptions = FilterPanelFixtures.options,
                                scope = ControlPanelFixtures.scope(),
                            ),
                    )
                }
            }
        }
    }
    withStatusBar()
}

/** The Read view of [ReadViewFixtures]' thread in [palette]. */
internal fun AndroidComposeTestRule<*, MainActivity>.showControlRead(palette: OtusPalette = OtusPalette.Otus) {
    val fixtures = ReadViewFixtures
    runOnUiThread {
        activity.setContent {
            OtusTheme(palette) {
                ProvideTextStyle(panelTextStyle()) {
                    MessageScreen(
                        fixtures.threadRow,
                        fixtures.actions(),
                        null,
                        fixtures.read(fixtures.thread),
                        emptySet(),
                        onClose = {},
                    )
                }
            }
        }
    }
    withStatusBar()
}

/** Hands the window a status bar inset, as a phone has, so the full-bleed panel pads below it. */
internal fun AndroidComposeTestRule<*, MainActivity>.withStatusBar() {
    // After the first layout, which applies the window's own, empty insets.
    waitForIdle()
    runOnUiThread {
        val top = (STATUS_BAR_DP * activity.resources.displayMetrics.density).toInt()
        val insets =
            WindowInsetsCompat
                .Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, top, 0, 0))
                .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                .build()
        // The content view's child is the ComposeView, whose insets listener feeds WindowInsets.
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets)
    }
    waitForIdle()
}

/** Drags the list up by [distance] past the touch slop and holds it there, so nothing settles. */
internal fun AndroidComposeTestRule<*, MainActivity>.holdScrolledBy(distance: Dp) {
    onRoot().performTouchInput {
        down(center)
        moveBy(Offset(0f, -viewConfiguration.touchSlop))
        moveBy(Offset(0f, -distance.toPx()))
    }
    waitForIdle()
}

internal fun AndroidComposeTestRule<*, MainActivity>.panelShot(name: String) =
    onRoot().captureRoboImage("src/test/screenshots/control_panel/$name.png")

/** Taps the search pill, which focuses the field, and on the inbox grows its search room. */
internal fun ComposeTestRule.openSearch(prompt: String = "Search Inbox") {
    onNodeWithContentDescription(prompt).performClick()
    waitForIdle()
}

/** Opens the page's search by its [prompt] and shows all results, so the list and the search pills show. */
internal fun ComposeTestRule.openSearchResults(prompt: String = "Search Inbox") {
    onNodeWithContentDescription(prompt).performClick()
    searchField().performImeAction()
    waitForIdle()
}

/** The search field on screen: the inbox's pill itself, or the field a page without a search room opens. */
internal fun ComposeTestRule.searchField(): SemanticsNodeInteraction = onNode(hasSetTextAction())
