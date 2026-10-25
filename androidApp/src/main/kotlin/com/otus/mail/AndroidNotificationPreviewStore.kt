package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.feature.notifications.domain.NotificationPreviewStore
import com.otus.mail.feature.notifications.domain.NotificationPreviews

/** The notification preview choice, in app-private preferences that new-mail checks read too. */
class AndroidNotificationPreviewStore(
    context: Context,
) : NotificationPreviewStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun read(): NotificationPreviews = NotificationPreviews.decode(preferences.getString(PREVIEWS, null))

    override fun write(previews: NotificationPreviews) = preferences.edit { putString(PREVIEWS, previews.storageValue) }

    private companion object {
        const val PREFERENCES = "otus_mail_notifications"
        const val PREVIEWS = "previews"
    }
}
