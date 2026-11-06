package com.otus.mail.feature.notifications.data

import com.otus.mail.feature.notifications.domain.AlertedMail
import com.otus.mail.feature.notifications.domain.MailAlerts
import com.otus.mail.feature.notifications.domain.MailStateStore
import com.otus.mail.feature.notifications.domain.NewMail

/** Saved check states, read and written in memory. */
internal class States(
    var saved: Map<String, String> = emptyMap(),
) : MailStateStore {
    override fun read() = saved

    override fun write(values: Map<String, String>) {
        saved = values
    }
}

/** The alerts a check showed and cleared, with [alerted] already showing. */
internal class Alerts(
    val alerted: Set<AlertedMail> = emptySet(),
) : MailAlerts {
    val shown = mutableListOf<NewMail>()
    val cleared = mutableListOf<AlertedMail>()

    override fun showing() = alerted

    override fun show(mail: List<NewMail>) {
        shown += mail
    }

    override fun clear(mail: List<AlertedMail>) {
        cleared += mail
    }
}
