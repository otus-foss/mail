/*
 * RampenSau, https://github.com/meodai/rampensau, whose ramp generation this file ports.
 *
 * MIT License
 *
 * Copyright (c) 2021 David Aerne
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package com.otusfoss.mail.feature.theme.domain

import kotlin.math.pow

/** A color as a [hue] in degrees from 0 up to 360, and a [saturation] and [lightness] from 0 to 1. */
data class RampColor(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
)

/** How a ramp moves from one end of a range to the other: 0 maps to 0 and 1 to 1, not always evenly between. */
fun interface RampEasing {
    fun ease(progress: Float): Float

    companion object {
        /** Even steps, RampenSau's default for hue. */
        val linear = RampEasing { it }

        /** Steps that start small and grow, such as RampenSau's `x²` for saturation and `x^1.5` for lightness. */
        fun power(exponent: Float) = RampEasing { it.pow(exponent) }
    }
}

/** A saturation or lightness that goes from [start] to [end] along [easing]; [end] may be below [start]. */
data class RampRange(
    val start: Float,
    val end: Float,
    val easing: RampEasing = RampEasing.linear,
) {
    fun at(progress: Float): Float = start + (end - start) * easing.ease(progress)
}

/**
 * The hues of a ramp, RampenSau's `hStart`, `hCycles`, `hStartCenter`, and `hEasing`. The ramp
 * turns [cycles] times around the color wheel, backwards, and [center] places [start] on that
 * path: 1 puts it at the first color, a half in the middle, and 0 at the last.
 */
data class HueRange(
    val start: Float,
    val cycles: Float = 1f,
    val center: Float = MIDDLE,
    val easing: RampEasing = RampEasing.linear,
) {
    fun at(progress: Float): Float = normalizedHue(start + (1 - easing.ease(progress) - center) * FULL_TURN * cycles)
}

/**
 * [total] colors whose hues follow [hues] while [saturation] and [lightness] move along their
 * ranges, so the colors in between belong together.
 *
 * This ports `generateColorRamp` from RampenSau 2.4.0 by David Aerne (meodai),
 * https://github.com/meodai/rampensau, which is under the MIT licence: "Copyright (c) 2021 David
 * Aerne". The port keeps the maths and leaves RampenSau's random defaults to the caller, which
 * draws them from a seeded random so that a ramp can be made again.
 */
fun colorRamp(
    total: Int,
    hues: HueRange,
    saturation: RampRange,
    lightness: RampRange,
): List<RampColor> = rampProgress(total).map { RampColor(hues.at(it), saturation.at(it), lightness.at(it)) }

/** One color for each of [hueList], RampenSau's fixed hues, with [saturation] and [lightness] as in [colorRamp]. */
fun colorRamp(
    hueList: List<Float>,
    saturation: RampRange,
    lightness: RampRange,
): List<RampColor> =
    hueList.zip(rampProgress(hueList.size)) { hue, progress ->
        RampColor(normalizedHue(hue), saturation.at(progress), lightness.at(progress))
    }

/** [hue] turned into the range from 0 up to 360, such as 10 for -350 or for 370. */
fun normalizedHue(hue: Float): Float = hue.mod(FULL_TURN)

/** Where each of [total] colors sits on the ramp, from 0 for the first to 1 for the last. */
private fun rampProgress(total: Int): List<Float> = List(total) { if (total > 1) it / (total - 1f) else 0f }

/** Degrees in one turn of the color wheel. */
const val FULL_TURN = 360f

private const val MIDDLE = 0.5f
