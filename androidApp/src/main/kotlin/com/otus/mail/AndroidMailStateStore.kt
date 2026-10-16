package com.otus.mail

import android.content.Context
import com.otus.mail.feature.notifications.domain.MailStateStore

/** Each login's Email state from the last new-mail check, stored under the login id. */
class AndroidMailStateStore(
    context: Context,
) : MailStateStore {
    private val map = PreferenceMap(context, "otus_mail_states")

    override fun read(): Map<String, String> = map.read()

    override fun write(states: Map<String, String>) = map.write(states)
}
