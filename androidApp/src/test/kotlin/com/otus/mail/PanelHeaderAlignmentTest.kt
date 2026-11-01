package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.input.elements.SearchField
import com.otus.designsystem.shared.panels.components.ContextPanel
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelHeaderAlignmentTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun iconAndTextStayAnchoredThroughModeChangesTypingAndClearing() {
        assertHeaderAnchors()
    }

    @Test
    fun sharedHeaderCentersPlainTitlesAndSearchFieldsAtTheSamePosition() {
        val searching = mutableStateOf(false)
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    ContextPanel(navigation = { IconButton("Panel navigation", {}) { Text("<") } }) {
                        if (searching.value) {
                            SearchField("", {}, "Search title")
                        } else {
                            Text("Inbox")
                        }
                    }
                }
            }
        }
        val icon = navigation("Panel navigation")
        val title = compose.onNodeWithText("Inbox", useUnmergedTree = true)
        val titleBaseline = baseline(title)
        val titleX = title.fetchSemanticsNode().positionInRoot.x
        compose.runOnIdle { searching.value = true }
        val search = compose.onNodeWithText("Search title", useUnmergedTree = true)
        assertEquals(icon, navigation("Panel navigation"))
        assertEquals(titleBaseline, baseline(search), 0.5f)
        assertEquals(titleX, search.fetchSemanticsNode().positionInRoot.x, 0.5f)
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun enlargedTextKeepsTheSameAnchorsWithoutWrappingThePrompt() {
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    OtusTheme { InboxScreen(emptyList()) }
                }
            }
        }
        val layouts = textLayouts(prompt())
        assertEquals("The prompt stays on one line at larger text sizes", 1, layouts.single().lineCount)
        assertHeaderAnchors()
    }

    private fun assertHeaderAnchors() {
        val originalIcon = navigation("Open navigation")
        val originalBaseline = baseline(prompt())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Search Inbox").performClick()
        repeat(20) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals("Icon moved in entering frame $frame", originalIcon, navigation("Back to inbox"))
            assertEquals(
                "Text baseline moved in entering frame $frame",
                originalBaseline,
                baseline(placeholder()),
                0.5f,
            )
        }
        compose.mainClock.autoAdvance = true
        val emptyField = field().fetchSemanticsNode().boundsInRoot
        field().performTextInput("hello")
        assertEquals("Typing must not resize the header", originalIcon, navigation("Back to inbox"))
        assertEquals(emptyField.top, field().fetchSemanticsNode().boundsInRoot.top, 0.5f)
        assertEquals(emptyField.height, field().fetchSemanticsNode().boundsInRoot.height, 0.5f)
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals(originalBaseline, baseline(placeholder()), 0.5f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        repeat(20) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals("Icon moved in returning frame $frame", originalIcon, navigation("Open navigation"))
            assertEquals("Text baseline moved in returning frame $frame", originalBaseline, baseline(prompt()), 0.5f)
        }
        compose.mainClock.autoAdvance = true
        val height =
            compose
                .onNodeWithContentDescription("Search Inbox")
                .fetchSemanticsNode()
                .boundsInRoot.height
        assertTrue(
            "The prompt keeps its accessible touch target",
            height >= 48 * compose.activity.resources.displayMetrics.density,
        )
    }

    private fun prompt(): SemanticsNodeInteraction = compose.onNodeWithText("Search Inbox", useUnmergedTree = true)

    private fun placeholder(): SemanticsNodeInteraction = compose.onNodeWithText("Search Inbox", useUnmergedTree = true)

    private fun field(): SemanticsNodeInteraction = compose.searchField()

    private fun navigation(description: String): Rect =
        compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot

    private fun baseline(node: SemanticsNodeInteraction): Float =
        node.fetchSemanticsNode().positionInRoot.y + textLayouts(node).single().firstBaseline

    private fun textLayouts(node: SemanticsNodeInteraction): List<TextLayoutResult> =
        buildList { node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(this) } }
}
