package com.otus.mail.feature.notifications.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.feature.notifications.domain.AlertedMail
import com.otus.mail.feature.notifications.domain.MailAlerts
import com.otus.mail.feature.notifications.domain.NewMail
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** An account removed while a new-mail check runs gets no alert, and its state stays forgotten. */
class NewMailAccountRemovalTest {
    @Test
    fun anAccountRemovedDuringTheCheckGetsNoAlert() =
        runTest {
            val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
            val states = States(mapOf("ada" to "e1"))
            val shown = mutableListOf<NewMail>()
            val alerts =
                object : MailAlerts {
                    override fun showing() = emptySet<AlertedMail>()

                    override fun show(mail: List<NewMail>) {
                        shown += mail
                    }

                    override fun clear(mail: List<AlertedMail>) = Unit
                }
            val server =
                stalwartLike(api = { body ->
                    if ("Email/changes" in body) {
                        """{"methodResponses": [["Email/changes", {"oldState": "e1", "newState": "e2",
                           "hasMoreChanges": false, "created": ["m1"]}, "c0"]]}"""
                    } else {
                        // The user removes the account while the server answers the last request.
                        store.remove(ADA_LOGIN.id)
                        """{"methodResponses": [
                           ["Mailbox/get", {"state": "b1", "list": [{"id": "i", "role": "inbox"}]}, "c0"],
                           ["Email/get", {"state": "e2", "list": [{"id": "m1", "mailboxIds": {"i": true},
                             "keywords": {}, "from": [{"email": "grace@example.org"}], "subject": "Hello",
                             "preview": "Preview", "receivedAt": "2026-10-14T08:00:00Z"}]}, "c1"]]}"""
                    }
                })
            JmapNewMailChecker(
                JmapConnections(JmapClient(server), store),
                Mailboxes(InMemoryMailCache()),
                states.typed,
            ).check(alerts)
            assertEquals(emptyList(), shown)
            assertEquals(emptyMap(), states.saved)
        }
}
