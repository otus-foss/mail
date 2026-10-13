package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.settings.presentation.ThemePreferenceStore

class AndroidThemePreferenceStore(
    context: Context,
) : ThemePreferenceStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            "otus_mail_theme",
            Context.MODE_PRIVATE,
        )

    override fun read(): String? = preferences.getString(THEME, null)

    override fun write(value: String) {
        preferences.edit { putString(THEME, value) }
    }

    private companion object {
        const val THEME = "theme"
    }
}
