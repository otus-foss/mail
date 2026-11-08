package com.otus.mail.feature.theme.presentation.components

import com.otus.designsystem.theme.colors.ThemeColor
import com.otus.mail.feature.theme.presentation.LowContrast
import kotlin.test.Test
import kotlin.test.assertEquals

class LowContrastDetailTest {
    @Test
    fun aRatioJustBelowTheMinimumNeverReadsAsReachingIt() {
        val issue = LowContrast(ThemeColor.MutedText, ThemeColor.Page, ratio = 4.49f, required = 4.5f)
        assertEquals("4.4:1, needs 4.5:1", lowContrastDetail(issue, more = 0))
    }

    @Test
    fun theOtherPairsAreCountedAndWholeRatiosLoseTheirDecimal() {
        val issue = LowContrast(ThemeColor.AccentOutline, ThemeColor.Raised, ratio = 2f, required = 3f)
        assertEquals("2:1, needs 3:1 · 2 more", lowContrastDetail(issue, more = 2))
    }
}
