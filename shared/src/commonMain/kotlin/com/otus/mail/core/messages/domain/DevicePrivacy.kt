package com.otus.mail.core.messages.domain

/**
 * The device protections a platform can apply. With [screen], Mail can keep its screen out of
 * screenshots, screen recordings, and recent apps; with [keyboard], it can ask the keyboard not to
 * learn from what the user types.
 */
data class DevicePrivacy(
    val screen: Boolean = false,
    val keyboard: Boolean = false,
)
