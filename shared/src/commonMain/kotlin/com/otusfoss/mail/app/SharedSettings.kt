package com.otusfoss.mail.app

import com.otusfoss.core.settings.presentation.AnimationSettingsState
import com.otusfoss.core.settings.presentation.AppIconSettingsState
import com.otusfoss.core.settings.presentation.DateFormatSettingsState
import com.otusfoss.core.settings.presentation.FontSettingsState
import com.otusfoss.core.settings.presentation.ThemeSettingsState
import com.otusfoss.core.storage.inMemoryChoice

/**
 * The suite's settings that Mail shows on the shared settings pages and applies to every page.
 * Each one is kept in memory unless the platform passes its saved state. [appIcon] is null where
 * the platform cannot switch its icon; the launch animation then uses the accent.
 */
class SharedSettings(
    val dateFormat: DateFormatSettingsState = DateFormatSettingsState(inMemoryChoice()),
    val theme: ThemeSettingsState = ThemeSettingsState(inMemoryChoice()),
    val animation: AnimationSettingsState = AnimationSettingsState(inMemoryChoice()),
    val font: FontSettingsState = FontSettingsState(inMemoryChoice()),
    val appIcon: AppIconSettingsState? = null,
)
