package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.core.preferences.StringMapStore

/**
 * A string map kept in app-private shared preferences, replaced whole on each write. With [commit],
 * a write reaches the disk before it returns.
 */
class PreferenceMap(
    context: Context,
    name: String,
    private val commit: Boolean = false,
) : StringMapStore {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun read(): Map<String, String> =
        preferences.all
            .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
            .toMap()

    override fun write(values: Map<String, String>) {
        preferences.edit(commit) {
            clear()
            values.forEach { (key, value) -> putString(key, value) }
        }
    }
}
