package com.otus.mail.feature.theme.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorRampTest {
    private val flat = RampRange(start = 0.5f, end = 0.5f)

    @Test
    fun huesStepEvenlyBackwardsFromTheStartWhenItIsCentredOnTheFirstColor() {
        val ramp = colorRamp(total = 5, HueRange(start = 200f, cycles = 0.5f, center = 1f), flat, flat)
        assertHues(listOf(200f, 155f, 110f, 65f, 20f), ramp.map { it.hue })
    }

    @Test
    fun aCentredStartSitsInTheMiddleAndTheRangeWrapsAroundTheWheel() {
        val ramp = colorRamp(total = 3, HueRange(start = 10f, cycles = 0.25f), flat, flat)
        assertHues(listOf(55f, 10f, 325f), ramp.map { it.hue })
    }

    @Test
    fun easedRangesStillStartAndEndAtTheirEnds() {
        val saturation = RampRange(start = 0.4f, end = 0.35f, easing = RampEasing.power(2f))
        val lightness = RampRange(start = 0.05f, end = 0.9f, easing = RampEasing.power(1.5f))
        val ramp = colorRamp(total = 9, HueRange(start = 0f), saturation, lightness)
        assertEquals(0.4f, ramp.first().saturation, absoluteTolerance = TOLERANCE)
        assertEquals(0.35f, ramp.last().saturation, absoluteTolerance = TOLERANCE)
        assertEquals(0.05f, ramp.first().lightness, absoluteTolerance = TOLERANCE)
        assertEquals(0.9f, ramp.last().lightness, absoluteTolerance = TOLERANCE)
    }

    @Test
    fun aPowerEasingHoldsBackTheMiddleOfTheRange() {
        val lightness = RampRange(start = 0f, end = 1f, easing = RampEasing.power(2f))
        val middle = colorRamp(total = 3, HueRange(start = 0f), flat, lightness)[1]
        assertEquals(0.25f, middle.lightness, absoluteTolerance = TOLERANCE)
    }

    @Test
    fun fixedHuesSetTheLengthAndKeepTheirOrder() {
        val ramp = colorRamp(hueList = listOf(30f, 390f, -60f), flat, RampRange(start = 0.2f, end = 0.8f))
        assertHues(listOf(30f, 30f, 300f), ramp.map { it.hue })
        assertEquals(listOf(0.2f, 0.5f, 0.8f), ramp.map { it.lightness })
    }

    @Test
    fun aSingleColorSitsAtTheStartOfEveryRange() {
        val ramp = colorRamp(total = 1, HueRange(start = 90f, center = 1f), flat, RampRange(start = 0.1f, end = 0.9f))
        assertEquals(listOf(RampColor(hue = 90f, saturation = 0.5f, lightness = 0.1f)), ramp)
    }

    private fun assertHues(
        expected: List<Float>,
        actual: List<Float>,
    ) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (hue, ramped) -> assertEquals(hue, ramped, absoluteTolerance = TOLERANCE) }
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
