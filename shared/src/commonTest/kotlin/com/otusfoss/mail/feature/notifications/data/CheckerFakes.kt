package com.otusfoss.mail.feature.notifications.data

import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.notifications.domain.AlertedMail
import com.otusfoss.mail.feature.notifications.domain.MailAlerts
import com.otusfoss.mail.feature.notifications.domain.MailStateStore
import com.otusfoss.mail.feature.notifications.domain.NewMail
import com.otusfoss.mail.testing.MemoryMapStore

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
