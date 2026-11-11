package com.otusfoss.mail.feature.theme.presentation

import androidx.compose.ui.graphics.Color
import com.otusfoss.core.designsystem.theme.colors.ThemeBase
import com.otusfoss.core.designsystem.theme.colors.ThemeChoice
import com.otusfoss.core.designsystem.theme.colors.ThemeColor
import com.otusfoss.core.designsystem.theme.colors.hslColor
import com.otusfoss.mail.feature.theme.domain.RampColor
import com.otusfoss.mail.feature.theme.domain.ThemeRamp

/**
 * A new custom theme in [ramp]'s colors, made from Light or Otus and selected, with [ramp]'s
 * accent. The user's other themes stay as they are. While [lowContrast] finds a text or the
 * accent hard to read, that color moves away from the surfaces a step at a time, so the theme
 * reaches every ratio the warning asks for.
 */
fun ThemeChoice.withRamp(ramp: ThemeRamp): ThemeChoice {
    val start = withBase(if (ramp.light) ThemeBase.Light else ThemeBase.Otus)
    var readable = ramp
    // Each step moves only the colors that still fail, so most of the set stays as generated.
    repeat(MAX_STEPS) {
        val choice = start.withColors(readable)
        val issues = choice.lowContrast()
        if (issues.isEmpty()) return choice
        readable = readable.clearer(issues.map { it.foreground }.toSet())
    }
    return start.withColors(readable)
}

private fun ThemeChoice.withColors(ramp: ThemeRamp): ThemeChoice =
    ramp.themeColors
        .fold(this) { choice, (color, value) -> choice.withColor(color, value.toColor()) }
        .withAccent(ramp.accent.toColor())

/** The set with each of [failing] a step lighter on a dark theme, or darker on a light one. */
private fun ThemeRamp.clearer(failing: Set<ThemeColor>): ThemeRamp {
    val step = if (light) -CLEARER_STEP else CLEARER_STEP
    val accentFails = ThemeColor.AccentOutline in failing || ThemeColor.AccentText in failing
    return copy(
        text = text.steppedBy(step, ThemeColor.Text in failing),
        secondaryText = secondaryText.steppedBy(step, ThemeColor.SecondaryText in failing),
        mutedText = mutedText.steppedBy(step, ThemeColor.MutedText in failing),
        accent = accent.steppedBy(step, accentFails),
    )
}

private fun RampColor.steppedBy(
    step: Float,
    failing: Boolean,
): RampColor = if (failing) copy(lightness = (lightness + step).coerceIn(0f, 1f)) else this

private val ThemeRamp.themeColors: List<Pair<ThemeColor, RampColor>>
    get() =
        listOf(
            ThemeColor.Page to page,
            ThemeColor.Raised to raised,
            ThemeColor.Controls to controls,
            ThemeColor.Borders to borders,
            ThemeColor.Text to text,
            ThemeColor.SecondaryText to secondaryText,
            ThemeColor.MutedText to mutedText,
        )

private fun RampColor.toColor(): Color = hslColor(hue, saturation, lightness)

/** Enough steps to carry any color from mid grey to white or black. */
private const val MAX_STEPS = 40
private const val CLEARER_STEP = 0.03f
