package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.settings.presentation.AppIconPreferenceStore

class AndroidAppIconPreferenceStore(
    context: Context,
) : AppIconPreferenceStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            "otus_mail_app_icon",
            Context.MODE_PRIVATE,
        )

    override fun read(): String? = preferences.getString(STYLE, null)

    override fun write(value: String) {
        preferences.edit { putString(STYLE, value) }
    }

    private companion object {
        const val STYLE = "style"
    }
}
