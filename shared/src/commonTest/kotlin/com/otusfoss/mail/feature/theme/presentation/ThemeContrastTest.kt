package com.otusfoss.mail.feature.theme.presentation

import androidx.compose.ui.graphics.Color
import com.otusfoss.core.designsystem.theme.colors.AccentPreset
import com.otusfoss.core.designsystem.theme.colors.ThemeBase
import com.otusfoss.core.designsystem.theme.colors.ThemeChoice
import com.otusfoss.core.designsystem.theme.colors.ThemeColor
import com.otusfoss.core.designsystem.theme.colors.contrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeContrastTest {
    @Test
    fun builtInThemesWithPresetAccentsNeverWarn() {
        val choices = ThemeBase.entries.flatMap { base -> AccentPreset.entries.map { ThemeChoice(base, it.color) } }
        choices.forEach { assertEquals(emptyList(), it.lowContrast(), "${it.base} with ${it.accent}") }
    }

    @Test
    fun blackOnWhiteIsTheHighestRatioAndPasses() {
        assertEquals(21f, contrast(Color.Black, Color.White), absoluteTolerance = 0.01f)
        val choice =
            ThemeChoice(ThemeBase.Light)
                .withColor(ThemeColor.Page, Color.White)
                .withColor(ThemeColor.Text, Color.Black)
        assertEquals(emptyList(), choice.lowContrast())
    }

    @Test
    fun textInThePageColorIsReportedAtOneToOne() {
        val page = ThemeBase.Otus.page
        val issue = ThemeChoice().withColor(ThemeColor.Text, page).lowContrast().first()
        assertEquals(LowContrast(ThemeColor.Text, ThemeColor.Page, ratio = 1f, required = 4.5f), issue)
    }

    @Test
    fun aPaleMutedTextIsReportedOnEverySurfaceItIsDrawnOnWorstFirst() {
        val issues = ThemeChoice(ThemeBase.Light).withColor(ThemeColor.MutedText, Color(0xFF9A9A9A)).lowContrast()
        assertEquals(listOf(ThemeColor.Page, ThemeColor.Raised), issues.map { it.background })
        assertTrue(issues.all { it.foreground == ThemeColor.MutedText && it.ratio < it.required })
        assertTrue(issues[0].ratio <= issues[1].ratio)
    }

    @Test
    fun anAccentOutlineNeedsThreeToOneOnlyWhenTheUserPickedTheAccent() {
        val preset = ThemeChoice(ThemeBase.Light, AccentPreset.Sky.color)
        val picked = preset.withAccent(Color(0xFF40B0F0))
        assertEquals(emptyList(), preset.lowContrast())
        val outlines = picked.lowContrast().filter { it.foreground == ThemeColor.AccentOutline }
        assertEquals(listOf(ThemeColor.Page, ThemeColor.Raised), outlines.map { it.background }.sorted())
        assertTrue(outlines.all { it.required == 3f })
    }

    @Test
    fun aDarkerPageKeepsTheThemesOwnTextReadable() {
        assertEquals(emptyList(), ThemeChoice().withColor(ThemeColor.Page, Color(0xFF050505)).lowContrast())
    }
}
