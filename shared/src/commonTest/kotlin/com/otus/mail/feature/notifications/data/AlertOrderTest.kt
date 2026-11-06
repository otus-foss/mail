package com.otus.mail.feature.notifications.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.feature.notifications.domain.AlertedMail
import com.otus.mail.feature.notifications.domain.MailAlerts
import com.otus.mail.feature.notifications.domain.MailStateStore
import com.otus.mail.feature.notifications.domain.NewMail
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.stalwartLike
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

    private val states =
        object : MailStateStore {
            override fun read() = mapOf(ADA_LOGIN.id to "e1")

            override fun write(values: Map<String, String>) = Unit
        }

    @Test
    fun oldAlertsGoBeforeNewOnesShow() =
        runTest {
            val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
            JmapNewMailChecker(JmapConnections(JmapClient(stalwartLike(api = ::reply)), store), states)
                .check(alerts)
            assertEquals(listOf("clear [m1]", "show [m6]"), events, "Android must not clear the new alert's summary")
        }

    private fun reply(body: String): String =
        if ("Email/changes" in body) {
            """{"methodResponses": [["Email/changes", {"oldState": "e1", "newState": "e2",
               "hasMoreChanges": false, "created": ["m6"], "updated": ["m1"]}, "c0"]]}"""
        } else {
            """{"methodResponses": [
               ["Mailbox/get", {"state": "b1", "list": [{"id": "i", "role": "inbox"}]}, "c0"],
               ["Email/get", {"state": "e2", "list": [${email(
                "m1",
                "{\"\$seen\": true}",
            )}, ${email("m6", "{}")}]}, "c1"]]}"""
        }

    private fun email(
        id: String,
        keywords: String,
    ) = """{"id": "$id", "mailboxIds": {"i": true}, "keywords": $keywords,
            "from": [{"email": "grace@example.org"}], "subject": "Hello", "receivedAt": "2026-10-14T08:00:00Z"}"""
}
