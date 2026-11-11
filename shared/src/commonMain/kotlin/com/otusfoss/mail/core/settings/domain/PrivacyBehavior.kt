package com.otusfoss.mail.core.settings.domain

import com.otusfoss.mail.core.messages.domain.DevicePrivacy

/**
 * What the device may keep of Mail. [screenSecurity] keeps Mail out of screenshots, screen
 * recordings, and recent apps, and [incognitoKeyboard] asks the keyboard not to learn from what the
 * user types, where [DevicePrivacy] allows them.
 */
data class PrivacyBehavior(
    val screenSecurity: Boolean = false,
    val incognitoKeyboard: Boolean = true,
)
