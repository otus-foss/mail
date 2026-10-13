package com.otus.mail

import android.content.Context
import androidx.core.content.edit

/** A string map kept in app-private shared preferences, replaced whole on each write. */
class PreferenceMap(
    context: Context,
    name: String,
) {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun read(): Map<String, String> =
        preferences.all
            .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
            .toMap()

    fun write(values: Map<String, String>) {
        preferences.edit {
            clear()
            values.forEach { (key, value) -> putString(key, value) }
        }
    }
}
