package com.otus.mail

import com.otus.settings.presentation.AnimationPreferenceStore
import platform.Foundation.NSUserDefaults

class IosAnimationPreferenceStore(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AnimationPreferenceStore {
    override fun read(): String? = preferences.stringForKey(SPEED)

    override fun write(value: String) {
        preferences.setObject(value, forKey = SPEED)
    }

    private companion object {
        const val SPEED = "otus.mail.animation_speed"
    }
}
