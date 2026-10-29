package com.otus.mail

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.mail.feature.welcome.presentation.screens.WelcomeScreen
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContextPanelTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun showPlayground() {
        compose.runOnUiThread { compose.activity.setContent { OtusTheme { WelcomeScreen() } } }
    }

    @Test
    fun searchSelectionAndFiltersAreControlledByScreen() {
        saveScreenshot("inbox")
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithText("People").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Search your mailbox…").performTextInput("meeting")
        val searchImage = saveScreenshot("search")
        val people = compose.onNodeWithText("People").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val gapY = (people.center.y - (36f / 2 + 1) * density).toInt()
        assertEquals(OtusColors.bgRegular.toArgb(), searchImage.getPixel(people.center.x.toInt(), gapY))
        assertTrue(people.height >= 48 * density)
        compose.onNodeWithContentDescription("Open filters").performClick()
        compose.onNodeWithText("Has attachments").performClick().assertIsOn()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        compose.onNodeWithContentDescription("Search mailbox").assertIsDisplayed()
        compose.onNodeWithText("Metadata").performClick()
        compose.onNodeWithText("Design review").assertIsDisplayed()
        saveScreenshot("metadata")
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun narrowLayoutKeepsActionFixedWhileChipsScroll() {
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        val before = compose.onNodeWithContentDescription("Open filters").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Mails").performScrollTo().assertIsDisplayed()
        val after = compose.onNodeWithContentDescription("Open filters").fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        compose.onNodeWithText("Mails").performClick().assertIsOn()
        saveScreenshot("search-narrow-scrolled")
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun heldEdgeDragsKeepPillsAndCutoutsTogether() {
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        assertEdgePullUnchanged("start", 80f)
        compose.onNodeWithText("Mails").performScrollTo().assertIsDisplayed()
        assertEdgePullUnchanged("end", -80f)
    }

    private fun assertEdgePullUnchanged(
        name: String,
        distance: Float,
    ) {
        val rail =
            compose.onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and
                    hasAnyDescendant(hasText("People")),
            )
        val bounds = rail.fetchSemanticsNode().boundsInRoot
        val before = saveScreenshot("edge-$name-before")
        rail.performTouchInput {
            down(center)
            moveBy(Offset(distance, 0f), delayMillis = 200)
        }
        val held = saveScreenshot("edge-$name-held")
        rail.performTouchInput { up() }
        val width = bounds.width.toInt()
        val height = bounds.height.toInt()
        val expected = IntArray(width * height)
        val actual = IntArray(width * height)
        before.getPixels(expected, 0, width, bounds.left.toInt(), bounds.top.toInt(), width, height)
        held.getPixels(actual, 0, width, bounds.left.toInt(), bounds.top.toInt(), width, height)
        assertArrayEquals("Held $name overscroll must not distort the pill rail", expected, actual)
    }

    @Test
    fun enlargedTextGrowsPillsAndKeepsSearchReachable() {
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    OtusTheme { WelcomeScreen() }
                }
            }
        }
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithText("People").performClick().assertIsOn()
        compose
            .onNodeWithText("Mails")
            .performScrollTo()
            .performClick()
            .assertIsOn()
        compose.onNodeWithContentDescription("Open filters").assertIsDisplayed()
        saveScreenshot("search-large-text")
    }

    private fun saveScreenshot(name: String): Bitmap =
        compose.windowBitmap().also { it.savePng("build/reports/context-panel/$name.png") }
}
