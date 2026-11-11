package com.otusfoss.mail.feature.notifications.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.notifications.domain.AlertedMail
import com.otusfoss.mail.feature.notifications.domain.MailAlerts
import com.otusfoss.mail.feature.notifications.domain.MailStateStore
import com.otusfoss.mail.feature.notifications.domain.NewMail
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.MemoryMapStore
import com.otusfoss.mail.testing.STALWART_SESSION
import com.otusfoss.mail.testing.stalwartLike

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

internal fun newMailChecker(
    connections: JmapConnections,
    states: States,
) = JmapNewMailChecker(connections, Mailboxes(InMemoryMailCache()), states.typed)

/** Runs one check for Ada against a server answering API calls with [reply]; the alerts it showed. */
internal suspend fun checkMail(
    states: States,
    alerts: Alerts = Alerts(),
    session: String = STALWART_SESSION,
    reply: (String) -> String,
): List<NewMail> {
    val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    newMailChecker(JmapConnections(JmapClient(stalwartLike(session, reply)), store), states).check(alerts)
    return alerts.shown
}

/** One email of an `Email/get` list, from Grace unless [name] says otherwise. */
internal fun emailJson(
    id: String,
    mailbox: String,
    keywords: String,
    name: String? = "Grace",
) = """{"id": "$id", "mailboxIds": {"$mailbox": true}, "keywords": $keywords,
        "from": [{${name?.let { "\"name\": \"$it\", " }.orEmpty()}"email": "grace@example.org"}],
        "subject": "Hello $id", "preview": "Preview", "receivedAt": "2026-10-14T08:00:00Z"}"""
