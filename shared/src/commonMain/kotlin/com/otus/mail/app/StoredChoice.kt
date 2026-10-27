package com.otus.mail.app

import com.otus.mail.core.preferences.KeyValueStore
import com.otus.settings.presentation.AppIconPreferenceStore
import com.otus.settings.presentation.ThemePreferenceStore

/** One choice, such as the theme or app icon, saved as text under [key] in [values]. */
class StoredChoice(
    private val values: KeyValueStore,
    private val key: String,
) : ThemePreferenceStore,
    AppIconPreferenceStore {
    override fun read(): String? = values.string(key)

    override fun write(value: String) = values.edit { put(key, value) }
}
