package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.otus.mail.designsystem.components.ChipPlacement
import com.otus.mail.designsystem.components.ContextPanel
import com.otus.mail.designsystem.elements.Chip
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.OtusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelFrameGeometryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val moved = mutableStateOf(false)
    private lateinit var pill: LayoutCoordinates

    @Test
    fun cutoutUsesThePaintedPillPositionOnEveryFrameDuringParentMovementAndResizing() {
        showPanel()
        compose.mainClock.autoAdvance = false
        listOf(true, false).forEach { target ->
            compose.runOnIdle { moved.value = target }
            repeat(20) { frame ->
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                compose.runOnIdle {
                    val position = pill.positionInRoot()
                    val density = compose.activity.resources.displayMetrics.density
                    val x = (position.x + pill.size.width / 2).toInt()
                    val y = (position.y + pill.size.height / 2 - (36f / 2 + 1) * density).toInt()
                    val bitmap = snapshot()
                    assertEquals(
                        "Pill and cutout must agree in frame $frame while moving toward $target",
                        OtusColors.bgRegular.toArgb(),
                        bitmap.getPixel(x, y),
                    )
                    bitmap.recycle()
                }
            }
        }
    }

    private fun showPanel() {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    val rootY = animateDpAsState(if (moved.value) 24.dp else 0.dp, tween(220))
                    val pillY = animateDpAsState(if (moved.value) 0.dp else (-12).dp, tween(220))
                    val headerHeight = animateDpAsState(if (moved.value) 90.dp else 60.dp, tween(220))
                    Box(Modifier.fillMaxSize().background(OtusColors.bgRegular)) {
                        ContextPanel(
                            modifier =
                                Modifier.offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        0,
                                        rootY.value.roundToPx(),
                                    )
                                },
                            placement = ChipPlacement.Overlapping,
                            chips = {
                                item("pill") {
                                    Chip(
                                        "People",
                                        {},
                                        Modifier
                                            .offset {
                                                androidx.compose.ui.unit.IntOffset(
                                                    0,
                                                    pillY.value.roundToPx(),
                                                )
                                            }.onGloballyPositioned { pill = it },
                                    )
                                }
                            },
                        ) {
                            BasicText("Header", Modifier.height(headerHeight.value))
                        }
                    }
                }
            }
        }
    }

    private fun snapshot(): Bitmap {
        val view = compose.activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
}
