package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.otus.mail.designsystem.components.ChipPlacement
import com.otus.mail.designsystem.components.ContextPanel
import com.otus.mail.designsystem.elements.Chip
import com.otus.mail.designsystem.elements.IconButton
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.OtusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelRailViewportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun scrolledPillReachesTheLeftPanelEdge() {
        assertScrolledEdge(LayoutDirection.Ltr)
    }

    @Test
    fun scrolledPillReachesTheRightPanelEdgeInRtl() {
        assertScrolledEdge(LayoutDirection.Rtl)
    }

    private fun assertScrolledEdge(direction: LayoutDirection) {
        showPanel(direction)
        val panel = compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithContentDescription("More filters").fetchSemanticsNode().boundsInRoot
        val rail = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
        val viewport = rail.fetchSemanticsNode().boundsInRoot
        assertEquals(panel.left, if (direction == LayoutDirection.Ltr) viewport.left else action.left, 0.5f)
        assertEquals(panel.right, if (direction == LayoutDirection.Rtl) viewport.right else action.right, 0.5f)

        rail.performSemanticsAction(SemanticsActions.ScrollBy) { it(24f, 0f) }
        val pill = compose.onNodeWithText("Sender filters").fetchSemanticsNode().boundsInRoot
        val x = if (direction == LayoutDirection.Ltr) panel.left + 4f else panel.right - 4f
        val bitmap = snapshot(direction)
        assertNotEquals(
            "A scrolled pill must remain visible inside the first 8 dp of the panel",
            OtusColors.bgRegular.toArgb(),
            bitmap.getPixel(x.toInt(), (pill.center.y + 4f).toInt()),
        )
        bitmap.recycle()
        assertEquals(action, compose.onNodeWithContentDescription("More filters").fetchSemanticsNode().boundsInRoot)
    }

    private fun showPanel(direction: LayoutDirection) {
        compose.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    OtusTheme {
                        Box(Modifier.fillMaxSize().background(OtusColors.bgRegular).padding(horizontal = 12.dp)) {
                            ContextPanel(
                                modifier = Modifier.testTag("panel"),
                                placement = ChipPlacement.Overlapping,
                                trailingAction = { IconButton("More filters", {}) { BasicText("+") } },
                                chips = {
                                    listOf(
                                        "Sender filters",
                                        "Date filters",
                                        "Inboxes filters",
                                        "Mail filters",
                                    ).forEach { label ->
                                        item(label) { Chip(label, {}) }
                                    }
                                },
                            ) { BasicText("Search your mailbox") }
                        }
                    }
                }
            }
        }
    }

    private fun snapshot(direction: LayoutDirection): Bitmap =
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/context-panel/scroll-edge-${direction.name.lowercase()}.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap
        }
}
