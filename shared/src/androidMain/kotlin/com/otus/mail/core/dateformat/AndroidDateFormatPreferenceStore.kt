package com.otus.mail.core.dateformat

import android.content.Context

class AndroidDateFormatPreferenceStore(
    context: Context,
) : DateFormatPreferenceStore {
    private val preferences = context.applicationContext.getSharedPreferences("otus_date_format", Context.MODE_PRIVATE)

    override fun read(): DateFormatPreference =
        DateFormatPreference.fromStorageValue(preferences.getString(PREFERENCE_KEY, null))

    override fun write(preference: DateFormatPreference) {
        preferences.edit().putString(PREFERENCE_KEY, preference.storageValue).apply()
    }

    private companion object {
        const val PREFERENCE_KEY = "date_format"
    }
}
