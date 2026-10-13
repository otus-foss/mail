package com.otus.mail

import com.otus.settings.presentation.ThemePreferenceStore
import platform.Foundation.NSUserDefaults

class IosThemePreferenceStore(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ThemePreferenceStore {
    override fun read(): String? = preferences.stringForKey(THEME)

    override fun write(value: String) {
        preferences.setObject(value, forKey = THEME)
    }

    private companion object {
        const val THEME = "otus.mail.theme"
    }
}
