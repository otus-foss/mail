package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.otus.designsystem.shared.dates.elements.calendarMonthBackground
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.colors.OtusColors
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
class CalendarMonthBackgroundTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun outlineRoundsOuterAndInwardCornersWithoutSeamsBetweenWeeks() {
        val bitmap = render(LayoutDirection.Ltr)
        val dark = OtusColors.bgDark.toArgb()
        val light = OtusColors.bgRegular.toArgb()
        assertEquals("Outer corner is cut away", dark, bitmap.getPixel(121, 1))
        assertEquals("Interior remains filled", light, bitmap.getPixel(129, 1))
        assertEquals("Inward curve fills the step corner", light, bitmap.getPixel(119, 47))
        assertEquals("Adjacent month remains visible", dark, bitmap.getPixel(112, 40))
        assertEquals("Weeks meet without a gap", light, bitmap.getPixel(160, 47))
        assertEquals("Weeks meet without a gap", light, bitmap.getPixel(160, 48))
    }

    @Test
    fun rightToLeftMirrorsBothCornerDirections() {
        val ltr = render(LayoutDirection.Ltr)
        val rtl = render(LayoutDirection.Rtl)
        listOf(121 to 1, 129 to 1, 119 to 47, 112 to 40, 160 to 47, 160 to 48).forEach { (x, y) ->
            assertEquals(ltr.getPixel(x, y), rtl.getPixel(ltr.width - 1 - x, y))
        }
    }

    private fun render(direction: LayoutDirection): Bitmap {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        Column(
                            Modifier
                                .requiredSize(280.dp, 96.dp)
                                .background(OtusColors.bgDark)
                                .semantics { contentDescription = "Month outline" },
                        ) {
                            repeat(2) { week ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .calendarMonthBackground(3, 31, week, OtusColors.bgRegular),
                                )
                            }
                        }
                    }
                }
            }
        }
        val bounds = compose.onNodeWithContentDescription("Month outline").fetchSemanticsNode().boundsInRoot
        return compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            Bitmap.createBitmap(
                bitmap,
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.width.toInt(),
                bounds.height.toInt(),
            )
        }
    }
}
