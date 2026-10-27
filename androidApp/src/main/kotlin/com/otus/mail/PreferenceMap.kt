package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.core.preferences.StringMapStore

/** A string map kept in app-private shared preferences, replaced whole on each write. */
class PreferenceMap(
    context: Context,
    name: String,
) : StringMapStore {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun read(): Map<String, String> =
        preferences.all
            .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
            .toMap()

    override fun write(values: Map<String, String>) {
        preferences.edit {
            clear()
            values.forEach { (key, value) -> putString(key, value) }
        }
    }
}
