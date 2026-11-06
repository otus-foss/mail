package com.otus.mail.feature.theme.domain

import kotlin.random.Random

/**
 * A generated set of theme colors: three surfaces, the borders between them, three text levels,
 * and the accent. In a [light] set the surfaces are bright and the text dark; otherwise the
 * other way round.
 */
data class ThemeRamp(
    val light: Boolean,
    val page: RampColor,
    val raised: RampColor,
    val controls: RampColor,
    val borders: RampColor,
    val text: RampColor,
    val secondaryText: RampColor,
    val mutedText: RampColor,
    val accent: RampColor,
)

/**
 * Theme colors around [baseHue], read off one [colorRamp] that runs from the raised surface to
 * the strongest text. The surfaces start at [baseHue] and the hues drift from there, while the
 * tint fades towards nearly grey text, so every color shares the base. The accent is [baseHue]
 * itself, saturated. [seed] draws how far and which way the hues drift, how strong the tint is,
 * and where lightness starts and ends; the same seed always gives the same set.
 */
fun themeRamp(
    baseHue: Float,
    light: Boolean,
    seed: Int,
): ThemeRamp {
    val random = Random(seed)
    val hues = HueRange(normalizedHue(baseHue), cycles = random.within(-MAX_DRIFT, MAX_DRIFT), center = 1f)
    val saturation =
        RampRange(
            start = random.within(MIN_TINT, MAX_TINT),
            end = random.within(MIN_GREY, MAX_GREY),
            easing = RampEasing.power(SATURATION_EASING),
        )
    val deep = random.within(0f, MAX_SURFACE_DEPTH)
    val strong = random.within(0f, MAX_DEPTH)
    val lightness =
        RampRange(
            start = if (light) 1 - deep / 2 else MIN_DARK_SURFACE + deep,
            end = if (light) MIN_DARK_SURFACE + strong else 1 - strong,
            easing = RampEasing.power(LIGHTNESS_EASING),
        )
    val ramp = colorRamp(RAMP_STEPS, hues, saturation, lightness)
    val (raised, page, borders, controls) = ramp
    val (mutedText, secondaryText, text) = ramp.takeLast(TEXT_LEVELS)
    val accent = RampColor(hues.start, random.within(MIN_ACCENT, MAX_ACCENT), accentLightness(light, random))
    return ThemeRamp(
        light = light,
        page = page,
        raised = raised,
        controls = controls,
        borders = borders,
        text = text,
        secondaryText = secondaryText,
        mutedText = mutedText,
        accent = accent,
    )
}

/** A mid lightness for a saturated accent: darker on light pages so that it stands out from them. */
private fun accentLightness(
    light: Boolean,
    random: Random,
): Float = (if (light) LIGHT_ACCENT else DARK_ACCENT) + random.within(0f, ACCENT_SPREAD)

private fun Random.within(
    from: Float,
    until: Float,
): Float = from + nextFloat() * (until - from)

/** Ten steps leave room for four surfaces at one end and three text levels at the other. */
private const val RAMP_STEPS = 10
private const val TEXT_LEVELS = 3

/** At most a quarter turn of the wheel, either way, so the surfaces and text keep the base hue's family. */
private const val MAX_DRIFT = 0.25f
private const val MIN_TINT = 0.1f
private const val MAX_TINT = 0.35f
private const val MIN_GREY = 0.03f
private const val MAX_GREY = 0.12f

/**
 * Saturation eases by `x²`, RampenSau's default. Lightness eases by `x^1.8`, steeper than its
 * `x^1.5`, so the page stays close to the raised surface: darker in a dark set, lighter in a light one.
 */
private const val SATURATION_EASING = 2f
private const val LIGHTNESS_EASING = 1.8f

/** RampenSau starts lightness at up to 0.1; Otus surfaces sit just above black, and text a little below white. */
private const val MIN_DARK_SURFACE = 0.03f
private const val MAX_SURFACE_DEPTH = 0.03f
private const val MAX_DEPTH = 0.06f
private const val MIN_ACCENT = 0.5f
private const val MAX_ACCENT = 0.75f
private const val DARK_ACCENT = 0.55f
private const val LIGHT_ACCENT = 0.32f
private const val ACCENT_SPREAD = 0.1f
