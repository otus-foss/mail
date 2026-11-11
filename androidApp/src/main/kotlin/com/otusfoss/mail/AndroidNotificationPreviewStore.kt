package com.otusfoss.mail

import android.content.Context
import com.otusfoss.core.storage.PreferenceMap
import com.otusfoss.mail.feature.notifications.domain.NotificationPreviewStore
import com.otusfoss.mail.feature.notifications.domain.PreviewChoices

/**
 * The notification preview choices, in app-private preferences that new-mail checks read too. The
 * choice for all mail keeps the key earlier versions used, so it carries over. The file holds only
 * these choices, so a write replaces it whole.
 */
class AndroidNotificationPreviewStore(
    context: Context,
) : NotificationPreviewStore {
    private val map = PreferenceMap(context, "otus_mail_notifications")

    override fun read(): PreviewChoices = PreviewChoices.decode(map.read())

    override fun write(choices: PreviewChoices) = map.write(choices.encode())
}
