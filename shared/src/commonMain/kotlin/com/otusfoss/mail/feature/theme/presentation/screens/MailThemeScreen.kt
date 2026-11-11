package com.otusfoss.mail.feature.theme.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.theme.colors.ThemeChoice
import com.otusfoss.core.settings.presentation.ThemeSettingsState
import com.otusfoss.core.settings.presentation.screens.ThemeSettingsScreen
import com.otusfoss.mail.feature.theme.presentation.components.LowContrastWarning
import com.otusfoss.mail.feature.theme.presentation.components.ThemeRampPicker
import com.otusfoss.mail.feature.theme.presentation.lowContrast

/**
 * The theme page with a warning while some of the user's colors are hard to read, and the picker
 * that makes a set of colors from one hue. The phone shows the picker's set until the theme changes.
 */
@Composable
fun MailThemeScreen(
    theme: ThemeSettingsState,
    onBack: () -> Unit,
) {
    var trying by remember(theme.choice) { mutableStateOf<ThemeChoice?>(null) }
    ThemeSettingsScreen(theme, onBack = onBack, preview = (trying ?: theme.choice).palette) {
        LowContrastWarning(theme.choice.lowContrast())
        ThemeRampPicker(theme.choice, onTry = { trying = it }, onSelect = theme::select)
    }
}
