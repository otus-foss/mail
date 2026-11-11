package com.otusfoss.mail.feature.theme.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.otusfoss.core.designsystem.shared.controls.components.ButtonPair
import com.otusfoss.core.designsystem.shared.controls.components.SegmentedToggle
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.settings.elements.HueSlider
import com.otusfoss.core.designsystem.shared.settings.elements.SwatchStack
import com.otusfoss.core.designsystem.theme.colors.OtusColors
import com.otusfoss.core.designsystem.theme.colors.OtusPalette
import com.otusfoss.core.designsystem.theme.colors.ThemeChoice
import com.otusfoss.core.designsystem.theme.colors.toHsl
import com.otusfoss.core.designsystem.theme.tokens.core.Spacing
import com.otusfoss.core.designsystem.theme.tokens.settings.ThemeTokens
import com.otusfoss.mail.feature.theme.domain.themeRamp
import com.otusfoss.mail.feature.theme.presentation.withRamp

/**
 * Makes a set of theme colors from one hue on the Theme page. The slider picks the hue, starting
 * at [choice]'s accent, the toggle picks dark or light mode, and the swatches show the set. Once
 * the user changes any of them, [onTry] gets each new set to preview. Shuffle draws another set
 * from the same hue; Apply runs [onSelect] with the set as a new custom theme, so the user's own
 * themes stay as they are.
 */
@Composable
fun ThemeRampPicker(
    choice: ThemeChoice,
    onTry: (ThemeChoice) -> Unit,
    onSelect: (ThemeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hue by rememberSaveable { mutableFloatStateOf(choice.accent.toHsl().hue) }
    var light by rememberSaveable { mutableStateOf(choice.palette.isLight) }
    var seed by rememberSaveable { mutableIntStateOf(0) }
    var touched by rememberSaveable(choice) { mutableStateOf(false) }
    val generated = remember(choice, hue, light, seed) { choice.withRamp(themeRamp(hue, light, seed)) }
    LaunchedEffect(generated, touched) { if (touched) onTry(generated) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ThemeTokens.previewGap)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.iconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwatchStack(generated.palette.swatches, size = ThemeTokens.sliderHeight)
            Text("Colors from one hue", color = OtusColors.text1)
        }
        val (_, saturation, lightness) = generated.accent.toHsl()
        HueSlider(
            hue,
            {
                hue = it
                touched = true
            },
            "Base hue",
            saturation = saturation,
            lightness = lightness,
        )
        SegmentedToggle(
            MODE_OPTIONS,
            selected = if (light) 1 else 0,
            onSelect = {
                light = it == 1
                touched = true
            },
            track = OtusColors.bgDark,
        )
        ButtonPair(
            secondary =
                UiAction("Shuffle", onClick = {
                    seed++
                    touched = true
                }),
            primary = UiAction("Apply", onClick = { onSelect(generated) }),
        )
    }
}

/** The surfaces, text levels, and accent of a generated set, from raised surfaces to the accent. */
private val OtusPalette.swatches: List<Color>
    get() = listOf(bgDark, bgRegular, bgLight, text3, text2, text1, accentRegular)

private val MODE_OPTIONS = listOf("Dark mode", "Light mode")
