package com.otusfoss.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otusfoss.core.settings.presentation.AnimationSettingsState
import com.otusfoss.core.settings.presentation.AppIconSettingsState
import com.otusfoss.core.settings.presentation.DateFormatSettingsState
import com.otusfoss.core.settings.presentation.ThemeSettingsState

/** The suite's settings that Mail shows on the shared settings pages. */
internal class SharedSettings(
    val dateFormat: DateFormatSettingsState,
    val theme: ThemeSettingsState,
    val animation: AnimationSettingsState,
    val appIcon: AppIconSettingsState?,
)

/** The [SharedSettings] for these states, kept while they stay the same. */
@Composable
internal fun rememberSharedSettings(
    dateFormat: DateFormatSettingsState,
    theme: ThemeSettingsState,
    animation: AnimationSettingsState,
    appIcon: AppIconSettingsState?,
): SharedSettings =
    remember(dateFormat, theme, animation, appIcon) {
        SharedSettings(dateFormat, theme, animation, appIcon)
    }
