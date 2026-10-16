package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.settings.presentation.AnimationPreferenceStore

class AndroidAnimationPreferenceStore(
    context: Context,
) : AnimationPreferenceStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            "otus_mail_animation",
            Context.MODE_PRIVATE,
        )

    override fun read(): String? = preferences.getString(SPEED, null)

    override fun write(value: String) {
        preferences.edit { putString(SPEED, value) }
    }

    private companion object {
        const val SPEED = "speed"
    }
}
