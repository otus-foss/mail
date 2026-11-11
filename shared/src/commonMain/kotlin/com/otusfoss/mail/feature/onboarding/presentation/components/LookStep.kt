package com.otusfoss.mail.feature.onboarding.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.shared.controls.elements.ActionButton
import com.otusfoss.core.designsystem.shared.onboarding.components.OnboardingStepBody
import com.otusfoss.core.designsystem.shared.pages.components.PageSection
import com.otusfoss.core.designsystem.shared.settings.components.ThemePhonePreview
import com.otusfoss.core.designsystem.shared.settings.components.rememberPhoneExpansion
import com.otusfoss.core.designsystem.theme.colors.ThemeChoice
import com.otusfoss.core.settings.presentation.ThemeSettingsState
import com.otusfoss.core.settings.presentation.components.ThemePicker

/**
 * Picks the theme with the Theme page's parts: the phone under the title shows the app in the picked
 * colors, or in the ones [controls] are trying, above the theme cards. A picked card applies at
 * once; colors being tried apply with Continue. Everything fits one screen without scrolling.
 */
@Composable
internal fun ColumnScope.LookStep(
    theme: ThemeSettingsState,
    controls: @Composable (onTry: (ThemeChoice) -> Unit) -> Unit,
    onNext: () -> Unit,
) {
    var trying by remember(theme.choice) { mutableStateOf<ThemeChoice?>(null) }
    val preview = (trying ?: theme.choice).palette
    val phone = rememberPhoneExpansion()
    OnboardingStepBody(
        "Pick a look",
        footer = {
            ActionButton(
                "Continue",
                {
                    // The colors being tried become the theme; a picked theme card already is.
                    trying?.let(theme::select)
                    onNext()
                },
                Modifier.fillMaxWidth(),
                primary = true,
            )
        },
    ) {
        ThemePhonePreview(preview, phone)
        PageSection("Any color you like") { controls { trying = it } }
        PageSection("Themes") { ThemePicker(theme.choice, theme::select, columns = THEME_COLUMNS) }
    }
}

/** Three theme cards a row, so the shipped themes take two rows and the step needs no scrolling. */
private const val THEME_COLUMNS = 3
