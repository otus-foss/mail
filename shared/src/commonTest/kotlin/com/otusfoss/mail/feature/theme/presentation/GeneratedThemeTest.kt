package com.otusfoss.mail.feature.theme.presentation

import androidx.compose.ui.graphics.luminance
import com.otusfoss.core.designsystem.theme.colors.ThemeBase
import com.otusfoss.core.designsystem.theme.colors.ThemeChoice
import com.otusfoss.core.designsystem.theme.colors.ThemeColor
import com.otusfoss.core.designsystem.theme.colors.toHsl
import com.otusfoss.mail.feature.theme.domain.themeRamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneratedThemeTest {
    @Test
    fun everyGeneratedThemeReachesTheRatiosTheWarningAsksFor() {
        for (light in listOf(false, true)) {
            for (hue in 0 until 360 step HUE_STEP) {
                repeat(SEEDS) { seed ->
                    val theme = ThemeChoice().withRamp(themeRamp(hue.toFloat(), light, seed))
                    assertEquals(emptyList(), theme.lowContrast(), "hue $hue, seed $seed, light $light")
                }
            }
        }
    }

    @Test
    fun aMutedTextAsDarkAsThePageIsLightenedUntilItReads() {
        val ramp = themeRamp(baseHue = 200f, light = false, seed = 1)
        val theme = ThemeChoice().withRamp(ramp.copy(mutedText = ramp.page))
        assertEquals(emptyList(), theme.lowContrast())
        assertTrue(theme.palette.text3.luminance() > theme.palette.bgRegular.luminance())
    }

    @Test
    fun applyingAddsANewCustomThemeAndLeavesTheSelectedOneAlone() {
        val own = ThemeChoice().withColor(ThemeColor.Page, ThemeBase.Ink.page)
        val generated = own.withRamp(themeRamp(baseHue = 30f, light = true, seed = 4))
        val custom = assertNotNull(generated.customTheme)
        assertEquals(own.customThemes + custom, generated.customThemes)
        assertEquals(ThemeBase.Light, custom.base)
        assertTrue(generated.palette.isLight)
    }

    @Test
    fun theAccentTakesTheBaseHue() {
        val generated = ThemeChoice().withRamp(themeRamp(baseHue = 120f, light = false, seed = 2))
        assertEquals(120f, generated.accent.toHsl().hue, absoluteTolerance = 2f)
    }

    private companion object {
        const val HUE_STEP = 15
        const val SEEDS = 6
    }
}
