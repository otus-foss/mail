package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_archive
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_drive_file_move
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.shared.actionbar.components.ActionBar
import com.otus.designsystem.shared.actionbar.components.BarAction
import com.otus.designsystem.shared.actionbar.components.BarContext
import com.otus.designsystem.shared.actionbar.components.BarToken
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.overlays.ActionBarTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/** The bottom action bar's states, geometry, motion, and semantics, as its spec describes them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionBarTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var context by mutableStateOf<BarContext?>(null)
    private var secondaryCount by mutableStateOf(3)
    private var archiveEnabled by mutableStateOf(true)
    private var primaryEnabled by mutableStateOf(true)
    private var shown by mutableStateOf(true)
    private var fontScale by mutableFloatStateOf(1f)
    private var rtl by mutableStateOf(false)

    @Before
    fun showBar() {
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                OtusTheme {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density, fontScale),
                        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    ) {
                        Box(Modifier.fillMaxSize().background(OtusColors.bgRegular)) {
                            if (shown) {
                                ActionBar(
                                    BarAction("Move", Res.drawable.symbol_drive_file_move, enabled = primaryEnabled) {},
                                    Modifier.align(Alignment.BottomCenter).padding(ActionBarTokens.barInset),
                                    secondary = secondary().take(secondaryCount),
                                    context = context,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun secondary() =
        listOf(
            BarAction("Archive", Res.drawable.symbol_archive, enabled = archiveEnabled) {},
            BarAction("Delete", Res.drawable.symbol_delete) {},
            BarAction("Flag", Res.drawable.symbol_flag) {},
        )

    @Test
    fun everyStateRenders() {
        saveScreenshot("no-context")
        contexts.forEach { (name, value) ->
            context = value
            saveScreenshot(name)
        }
        context = null
        (0..2).forEach { count ->
            secondaryCount = count
            saveScreenshot("secondary-$count")
        }
        secondaryCount = 3
        context = contexts.getValue("people")
        rtl = true
        saveScreenshot("people-rtl")
        rtl = false
        listOf(1.3f, 2f).forEach { scale ->
            fontScale = scale
            saveScreenshot("people-font-$scale")
        }
    }

    @Test
    fun thePrimaryIconAndItsBorderStayPutThroughTheTransition() {
        val shell = shellBounds("Move")
        val before = actionEnd(saveScreenshot("transition-start"), shell)
        compose.mainClock.autoAdvance = false
        context = contexts.getValue("people")
        repeat(TRANSITION_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            assertSamePixels("Frame $it", before, actionEnd(capture(), shell))
        }
        compose.mainClock.autoAdvance = true
        assertEquals(shell, shellBounds("Move, 5 people"))
    }

    @Test
    fun theActionBorderCoversTheShellBorderWhereTheyMeet() {
        context = contexts.getValue("people")
        val shell = shellBounds("Move, 5 people")
        val density = compose.activity.resources.displayMetrics.density
        val bitmap = saveScreenshot("border-overlap")
        val x = (shell.right - ActionBarTokens.collapsedActionWidth.value * density / 2).toInt()
        val edges = listOf(shell.top.toInt() + 1, shell.bottom.toInt() - 2)
        edges.forEach { y -> assertEquals("Edge at y=$y", OtusColors.accentRegular.toArgb(), bitmap.getPixel(x, y)) }
        val endX = shell.right.toInt() - 2
        assertEquals(OtusColors.accentRegular.toArgb(), bitmap.getPixel(endX, shell.center.y.toInt()))
    }

    @Test
    fun aDisabledPrimaryHasTheRegularStrokeInsteadOfTheAccent() {
        primaryEnabled = false
        val shell = shellBounds("Move")
        val bitmap = saveScreenshot("primary-disabled")
        assertEquals(OtusColors.strokeBgDark.toArgb(), bitmap.getPixel(shell.center.x.toInt(), shell.top.toInt() + 1))
        assertEquals(OtusColors.bgDark.toArgb(), bitmap.getPixel(shell.center.x.toInt(), shell.top.toInt() + 6))
    }

    @Test
    fun aBarThatAppearsWithAContextCollapsesIntoIt() {
        shown = false
        context = contexts.getValue("count")
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            shown = true
            Snapshot.sendApplyNotifications()
        }
        repeat(FIRST_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val shell = shellBounds("Move, 3 messages")
        val y = shell.center.y.toInt()
        assertEquals("Starts full", OtusColors.accentDark.toArgb(), capture().getPixel(shell.center.x.toInt(), y))
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("Ends collapsed", OtusColors.bgDark.toArgb(), capture().getPixel(shell.center.x.toInt(), y))
    }

    @Test
    fun disablingAnActionKeepsEverySlotInPlace() {
        val slots = listOf("Archive", "Delete", "Flag").map { boundsOf(it) }
        archiveEnabled = false
        assertEquals(slots, listOf("Archive", "Delete", "Flag").map { boundsOf(it) })
    }

    @Test
    fun theShellIsOneButtonThatNamesItsContext() {
        context = contexts.getValue("people")
        val shell = compose.onNodeWithContentDescription("Move, 5 people").fetchSemanticsNode()
        assertEquals(Role.Button, shell.config.getOrNull(SemanticsProperties.Role))
        compose.onAllNodes(hasText("Move") or hasText("5 people")).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Move", substring = true)).assertCountEquals(1)
    }

    @Test
    fun largeTextGrowsTheShellButNotTheGroup() {
        context = contexts.getValue("people")
        fontScale = 2f
        val density = compose.activity.resources.displayMetrics.density
        val group = boundsOf("Delete")
        val shell = shellBounds("Move, 5 people")
        assertTrue(shell.height > ActionBarTokens.height.value * density)
        assertEquals(shell.center.y, group.center.y, 1f)
    }

    private fun shellBounds(description: String): Rect =
        compose
            .onNode(hasContentDescription(description) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
            .fetchSemanticsNode()
            .boundsInRoot

    private fun boundsOf(label: String): Rect =
        compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot

    /** The pixels at the shell's end, clear of the collapsed action's rounded start. */
    private fun actionEnd(
        bitmap: Bitmap,
        shell: Rect,
    ): IntArray {
        val density = compose.activity.resources.displayMetrics.density
        val endRegion = ActionBarTokens.collapsedActionWidth - ActionBarTokens.radius
        val left = (shell.right - endRegion.value * density).toInt()
        val width = shell.right.toInt() - left
        val height = shell.height.toInt()
        return IntArray(width * height).also { bitmap.getPixels(it, 0, width, left, shell.top.toInt(), width, height) }
    }

    /** Equal within [CHANNEL_TOLERANCE] per channel: compositing a fading layer can shift a color by one step. */
    private fun assertSamePixels(
        message: String,
        expected: IntArray,
        actual: IntArray,
    ) {
        val moved =
            expected.indices.firstOrNull { i ->
                listOf(0, 8, 16, 24).any { shift ->
                    abs((expected[i] shr shift and 0xFF) - (actual[i] shr shift and 0xFF)) > CHANNEL_TOLERANCE
                }
            }
        assertTrue("$message: pixel $moved differs", moved == null)
    }

    private fun capture(): Bitmap {
        val view = compose.activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun saveScreenshot(name: String): Bitmap {
        val bitmap = compose.runOnIdle { capture() }
        val file = File("build/reports/action-bar/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private companion object {
        const val CHANNEL_TOLERANCE = 2
        const val FIRST_FRAMES = 3
        const val TRANSITION_FRAMES = 16
        val maren = ChipAvatar("MH", colorIndex = 1)
        val contexts =
            linkedMapOf(
                "person" to BarContext("Maren Holt", BarToken.Person(maren)),
                "people" to BarContext("5 people", BarToken.People(maren, 5)),
                "count" to BarContext("messages", BarToken.Count(3), description = "3 messages"),
                "date" to BarContext("10:00–11:30", BarToken.Date("Oct", "16")),
            )
    }
}
