package com.otus.mail.feature.notifications.data

import com.otus.mail.core.storage.DeviceStore
import com.otus.mail.feature.notifications.domain.AlertedMail
import com.otus.mail.feature.notifications.domain.MailAlerts
import com.otus.mail.feature.notifications.domain.MailStateStore
import com.otus.mail.feature.notifications.domain.NewMail
import com.otus.mail.testing.MemoryMapStore

/** Saved check states, read and written in memory. */
internal class States(
    saved: Map<String, String> = emptyMap(),
) : MemoryMapStore(saved) {
    val typed = DeviceStore(this, MailStateStore)
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
