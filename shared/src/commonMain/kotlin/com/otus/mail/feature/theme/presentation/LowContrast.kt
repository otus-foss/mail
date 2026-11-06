package com.otus.mail.feature.theme.presentation

import com.otus.designsystem.theme.colors.AccentPreset
import com.otus.designsystem.theme.colors.OtusPalette
import com.otus.designsystem.theme.colors.ThemeChoice
import com.otus.designsystem.theme.colors.ThemeColor
import com.otus.designsystem.theme.colors.contrast
import com.otus.designsystem.theme.colors.get

/** [foreground] drawn on [background] reaches only [ratio], below the [required] WCAG ratio. */
data class LowContrast(
    val foreground: ThemeColor,
    val background: ThemeColor,
    val ratio: Float,
    val required: Float,
)

/**
 * The pairs of the user's colors that are hard to read, lowest ratio first. A pair counts only
 * when the user picked one of its colors: a color of the custom theme, or an accent that is no
 * preset. Built-in themes with a preset accent never warn.
 */
fun ThemeChoice.lowContrast(): List<LowContrast> {
    val colors = palette
    return checkedPairs
        .filter { picked(it.foreground) || picked(it.background) }
        .map { it.measure(colors) }
        .filter { it.ratio < it.required }
        .sortedBy { it.ratio }
}

private fun ThemeChoice.picked(color: ThemeColor): Boolean =
    color in customColors || (color.isAccent && AccentPreset.entries.none { it.color == accent })

private class ContrastPair(
    val foreground: ThemeColor,
    val background: ThemeColor,
    val required: Float,
) {
    fun measure(colors: OtusPalette): LowContrast =
        LowContrast(foreground, background, contrast(colors[foreground], colors[background]), required)
}

/**
 * Where the app draws text and accents. Text needs WCAG AA's 4.5:1; an accent outline marks a
 * control rather than spelling words, so it needs 3:1. Borders only separate surfaces and are
 * not checked.
 */
private val checkedPairs =
    buildList {
        val surfaceText = listOf(ThemeColor.Text, ThemeColor.SecondaryText, ThemeColor.MutedText, ThemeColor.AccentText)
        for (surface in listOf(ThemeColor.Page, ThemeColor.Raised)) {
            surfaceText.forEach { add(ContrastPair(it, surface, TEXT_CONTRAST)) }
            add(ContrastPair(ThemeColor.AccentOutline, surface, OUTLINE_CONTRAST))
        }
        add(ContrastPair(ThemeColor.Text, ThemeColor.Controls, TEXT_CONTRAST))
        add(ContrastPair(ThemeColor.Text, ThemeColor.AccentFill, TEXT_CONTRAST))
    }

private const val TEXT_CONTRAST = 4.5f
private const val OUTLINE_CONTRAST = 3f
