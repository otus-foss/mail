package com.otus.mail.app

import com.otus.settings.presentation.AnimationSettingsState
import com.otus.settings.presentation.AppIconSettingsState
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.settings.presentation.ThemeSettingsState

/** The suite's settings that Mail shows on the shared settings pages. */
internal class SharedSettings(
    val dateFormat: DateFormatSettingsState,
    val theme: ThemeSettingsState,
    val animation: AnimationSettingsState,
    val appIcon: AppIconSettingsState?,
)
