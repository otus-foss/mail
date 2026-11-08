package com.otus.mail.feature.theme.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ThemeRampTest {
    @Test
    fun theSameSeedGivesTheSameSetAndAnotherSeedADifferentOne() {
        val ramp = themeRamp(baseHue = 140f, light = false, seed = 7)
        assertEquals(ramp, themeRamp(baseHue = 140f, light = false, seed = 7))
        assertNotEquals(ramp, themeRamp(baseHue = 140f, light = false, seed = 8))
    }

    @Test
    fun theRaisedSurfaceAndTheAccentTakeTheBaseHue() {
        val ramp = themeRamp(baseHue = 400f, light = true, seed = 3)
        assertEquals(40f, ramp.raised.hue, absoluteTolerance = 0.001f)
        assertEquals(40f, ramp.accent.hue, absoluteTolerance = 0.001f)
    }

    @Test
    fun aDarkSetRisesFromRaisedSurfacesToTextAndALightSetFalls() {
        repeat(SEEDS) { seed ->
            val dark = themeRamp(baseHue = 260f, light = false, seed = seed).lightnesses
            val light = themeRamp(baseHue = 260f, light = true, seed = seed).lightnesses
            assertEquals(dark.sorted(), dark, "dark set of seed $seed")
            assertEquals(light.sortedDescending(), light, "light set of seed $seed")
        }
    }

    @Test
    fun theTintFadesSoTextIsNearlyGrey() {
        repeat(SEEDS) { seed ->
            val ramp = themeRamp(baseHue = 20f, light = false, seed = seed)
            assertTrue(ramp.text.saturation < ramp.page.saturation, "seed $seed")
            assertTrue(ramp.accent.saturation > ramp.page.saturation, "seed $seed")
        }
    }

    private val ThemeRamp.lightnesses: List<Float>
        get() = listOf(raised, page, borders, controls, mutedText, secondaryText, text).map { it.lightness }

    private companion object {
        const val SEEDS = 20
    }
}
