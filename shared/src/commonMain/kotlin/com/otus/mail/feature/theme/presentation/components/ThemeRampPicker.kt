package com.otus.mail.feature.theme.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.otus.designsystem.shared.controls.components.ButtonPair
import com.otus.designsystem.shared.controls.components.PairButton
import com.otus.designsystem.shared.controls.components.SegmentedToggle
import com.otus.designsystem.shared.settings.elements.ColorSlider
import com.otus.designsystem.shared.settings.elements.SwatchStack
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.colors.OtusPalette
import com.otus.designsystem.theme.colors.ThemeChoice
import com.otus.designsystem.theme.colors.hslColor
import com.otus.designsystem.theme.colors.toHsl
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.settings.ThemeTokens
import com.otus.mail.feature.theme.domain.FULL_TURN
import com.otus.mail.feature.theme.domain.themeRamp
import com.otus.mail.feature.theme.presentation.withRamp

/**
 * Makes a set of theme colors from one hue on the Theme page. The slider picks the hue, starting
 * at [choice]'s accent, the toggle picks dark or light pages, and the swatches show the set.
 * Shuffle draws another set from the same hue; Apply runs [onSelect] with the set as a new custom
 * theme, so the user's own themes stay as they are.
 */
@Composable
fun ThemeRampPicker(
    choice: ThemeChoice,
    onSelect: (ThemeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hue by rememberSaveable { mutableFloatStateOf(choice.accent.toHsl().hue) }
    var light by rememberSaveable { mutableStateOf(choice.palette.isLight) }
    var seed by rememberSaveable { mutableIntStateOf(0) }
    val generated = remember(choice, hue, light, seed) { choice.withRamp(themeRamp(hue, light, seed)) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ThemeTokens.previewGap)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PanelTokens.iconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwatchStack(generated.palette.swatches)
            Text("Colors from one hue", color = OtusColors.text1)
        }
        ColorSlider(hue, { hue = it }, hueTrack(generated.accent), "Base hue", range = 0f..FULL_TURN)
        SegmentedToggle(PAGE_OPTIONS, selected = if (light) 1 else 0, onSelect = { light = it == 1 })
        ButtonPair(
            secondary = PairButton("Shuffle", onClick = { seed++ }),
            primary = PairButton("Apply", onClick = { onSelect(generated) }),
        )
    }
}

/** The surfaces, text levels, and accent of a generated set, from raised surfaces to the accent. */
private val OtusPalette.swatches: List<Color>
    get() = listOf(bgDark, bgRegular, bgLight, text3, text2, text1, accentRegular)

/** Every hue at [accent]'s saturation and lightness, so the track shows the accent each hue would give. */
private fun hueTrack(accent: Color): Brush {
    val (_, saturation, lightness) = accent.toHsl()
    return Brush.horizontalGradient(List(HUE_STOPS + 1) { hslColor(FULL_TURN * it / HUE_STOPS, saturation, lightness) })
}

private val PAGE_OPTIONS = listOf("Dark pages", "Light pages")
private const val HUE_STOPS = 12
