package com.otusfoss.mail.feature.notifications.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.feature.notifications.domain.AlertedMail
import com.otusfoss.mail.feature.notifications.domain.MailAlerts
import com.otusfoss.mail.feature.notifications.domain.NewMail
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** One check that both clears an old alert and shows new mail for the same account. */
class AlertOrderTest {
    private val events = mutableListOf<String>()

    private val alerts =
        object : MailAlerts {
            override fun showing() = setOf(AlertedMail(ADA_LOGIN.id, "m1"))

            override fun show(mail: List<NewMail>) {
                events += "show ${mail.map { it.emailId }}"
            }

            override fun clear(mail: List<AlertedMail>) {
                events += "clear ${mail.map { it.emailId }}"
            }
        }

    private val states = States(mapOf(ADA_LOGIN.id to "e1"))

    @Test
    fun oldAlertsGoBeforeNewOnesShow() =
        runTest {
            val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
            JmapNewMailChecker(
                JmapConnections(JmapClient(stalwartLike(api = ::reply)), store),
                Mailboxes(InMemoryMailCache()),
                states.typed,
            ).check(alerts)
            assertEquals(listOf("clear [m1]", "show [m6]"), events, "Android must not clear the new alert's summary")
        }

    private fun reply(body: String): String =
        if ("Email/changes" in body) {
            """{"methodResponses": [["Email/changes", {"oldState": "e1", "newState": "e2",
               "hasMoreChanges": false, "created": ["m6"], "updated": ["m1"]}, "c0"]]}"""
        } else {
            // Only the first request of the check reads the folders; later ones use the kept ones.
            val folders = "Mailbox/get" in body
            val mailboxes = if (folders) INBOX_FOLDER else ""
            """{"methodResponses": [$mailboxes
               ["Email/get", {"state": "e2", "list": [${email(
                "m1",
                "{\"\$seen\": true}",
            )}, ${email("m6", "{}")}]}, "${if (folders) "c1" else "c0"}"]]}"""
        }

    private fun email(
        id: String,
        keywords: String,
    ) = """{"id": "$id", "mailboxIds": {"i": true}, "keywords": $keywords,
            "from": [{"email": "grace@example.org"}], "subject": "Hello", "receivedAt": "2026-10-14T08:00:00Z"}"""

    private companion object {
        const val INBOX_FOLDER = """["Mailbox/get", {"state": "b1", "list": [{"id": "i", "role": "inbox"}]}, "c0"],"""
    }
}
