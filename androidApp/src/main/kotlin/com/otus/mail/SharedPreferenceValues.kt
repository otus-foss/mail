package com.otus.mail

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.otus.mail.core.preferences.KeyValueEditor
import com.otus.mail.core.preferences.KeyValueStore

/** A [KeyValueStore] in the app-private shared preferences file [name]. */
class SharedPreferenceValues(
    context: Context,
    name: String,
) : KeyValueStore {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun string(key: String): String? = preferences.getString(key, null)

    override fun flag(key: String): Boolean? = if (key in preferences) preferences.getBoolean(key, false) else null

    override fun int(key: String): Int? = if (key in preferences) preferences.getInt(key, 0) else null

    override fun edit(changes: KeyValueEditor.() -> Unit) {
        preferences.edit { Editor(this).changes() }
    }

    private class Editor(
        private val editor: SharedPreferences.Editor,
    ) : KeyValueEditor {
        override fun put(
            key: String,
            value: String,
        ) {
            editor.putString(key, value)
        }

        override fun put(
            key: String,
            value: Boolean,
        ) {
            editor.putBoolean(key, value)
        }

        override fun put(
            key: String,
            value: Int,
        ) {
            editor.putInt(key, value)
        }
    }
}
