package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.feature.notifications.domain.NotificationPreviewStore
import com.otus.mail.feature.notifications.domain.PreviewChoices

/**
 * The notification preview choices, in app-private preferences that new-mail checks read too. The
 * choice for all mail keeps the key earlier versions used, so it carries over. The file holds only
 * these choices, so a write replaces it whole.
 */
class AndroidNotificationPreviewStore(
    context: Context,
) : NotificationPreviewStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun read(): PreviewChoices =
        PreviewChoices.decode(
            preferences.all
                .mapNotNull { (key, value) ->
                    (value as? String)?.let { key to it }
                }.toMap(),
        )

    override fun write(choices: PreviewChoices) =
        preferences.edit {
            clear()
            choices.encode().forEach { (key, value) -> putString(key, value) }
        }

    private companion object {
        const val PREFERENCES = "otus_mail_notifications"
    }
}
