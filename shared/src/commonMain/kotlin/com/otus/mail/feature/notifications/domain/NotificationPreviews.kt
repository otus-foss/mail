package com.otus.mail.feature.notifications.domain

/**
 * What a new-mail alert shows. [Always] shows the sender, subject, and start of the message, also
 * on the lock screen unless the device hides sensitive content. [Unlocked] keeps alerts off the
 * lock screen and shows them in full once the device is unlocked. [Never] says only "New mail".
 */
enum class NotificationPreviews {
    Always,
    Unlocked,
    Never,
    ;

    val storageValue: String get() = name.lowercase()

    companion object {
        val default: NotificationPreviews = Unlocked

        /** The choice saved as [stored], else [default]. */
        fun decode(stored: String?): NotificationPreviews = entries.firstOrNull { it.storageValue == stored } ?: default
    }
}

/** App-private storage for the [NotificationPreviews] choice. */
interface NotificationPreviewStore {
    fun read(): NotificationPreviews

    fun write(previews: NotificationPreviews)
}
