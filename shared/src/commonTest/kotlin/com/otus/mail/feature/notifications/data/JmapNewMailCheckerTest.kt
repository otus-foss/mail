package com.otus.mail.feature.notifications.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.feature.notifications.domain.AlertedMail
import com.otus.mail.feature.notifications.domain.MailAlerts
import com.otus.mail.feature.notifications.domain.NewMail
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.SUBMISSION_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class JmapNewMailCheckerTest {
    private val login = ADA_LOGIN

    private suspend fun check(
        states: States,
        alerts: Alerts = Alerts(),
        session: String = STALWART_SESSION,
        reply: (String) -> String,
    ): List<NewMail> {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        checker(JmapConnections(JmapClient(stalwartLike(session, reply)), store), states).check(alerts)
        return alerts.shown
    }

    private fun checker(
        connections: JmapConnections,
        states: States,
    ) = JmapNewMailChecker(connections, Mailboxes(InMemoryMailCache()), states.typed)

    @Test
    fun theFirstCheckOnlyRemembersTheState() =
        runTest {
            val states = States()
            val shown =
                check(states) {
                    assertTrue("\"ids\":[]" in it, it)
                    """{"methodResponses": [["Email/get", {"state": "e1", "list": []}, "c0"]]}"""
                }
            assertEquals(emptyList(), shown)
            assertEquals(mapOf("ada" to "e1"), states.saved)
        }

    @Test
    fun onlyUnreadInboxMailIsShown() =
        runTest {
            val states = States(mapOf("ada" to "e1"))
            val shown =
                check(states) { body ->
                    if ("Email/changes" in body) {
                        assertTrue("\"sinceState\":\"e1\"" in body, body)
                        """{"methodResponses": [["Email/changes", {"oldState": "e1", "newState": "e2",
                           "hasMoreChanges": false, "created": ["m1", "m2", "m3", "m4"], "updated": ["m0"]}, "c0"]]}"""
                    } else {
                        assertTrue("\"ids\":[\"m1\",\"m2\",\"m3\",\"m4\"]" in body, body)
                        """{"methodResponses": [
                           ["Mailbox/get", {"state": "b1", "list": [{"id": "i", "role": "inbox"}, {"id": "a", "role": "archive"}]}, "c0"],
                           ["Email/get", {"state": "e2", "list": [
                             ${email("m1", "i", "{}", "Grace")},
                             ${email("m2", "i", "{\"\$seen\": true}")},
                             ${email("m3", "a", "{}")},
                             ${email("m4", "i", "{\"\$draft\": true}")}]}, "c1"]]}"""
                    }
                }
            assertEquals(
                listOf(
                    NewMail("ada", "Ada", "m1", "Grace", "Hello m1", "Preview", Instant.parse("2026-10-14T08:00:00Z"))
                        .copy(mailboxRoles = setOf(MailboxRole.Inbox, MailboxRole.Archive)),
                ),
                shown,
            )
            assertEquals(mapOf("ada" to "e2"), states.saved)
        }

    @Test
    fun aSenderWithoutANameShowsTheAddressAndAnAccountThatSendsCanReply() =
        runTest {
            val shown =
                check(States(mapOf("ada" to "e1")), session = SUBMISSION_SESSION) { body ->
                    if ("Email/changes" in body) {
                        """{"methodResponses": [["Email/changes", {"oldState": "e1", "newState": "e2",
                           "hasMoreChanges": false, "created": ["m1"]}, "c0"]]}"""
                    } else {
                        """{"methodResponses": [
                           ["Mailbox/get", {"state": "b1", "list": [{"id": "i", "role": "inbox"}]}, "c0"],
                           ["Email/get", {"state": "e2", "list": [${email("m1", "i", "{}", null)}]}, "c1"]]}"""
                    }
                }
            assertEquals("grace@example.org", shown.single().sender)
            assertTrue(shown.single().canSend)
        }

    @Test
    fun followsChangePagesFromTheLatestState() =
        runTest {
            val states = States(mapOf("ada" to "e1"))
            val sent = mutableListOf<String>()
            check(states) { body ->
                sent += body
                when {
                    "\"sinceState\":\"e1\"" in body -> {
                        """{"methodResponses": [["Email/changes", {"oldState": "e1", "newState": "e2",
                           "hasMoreChanges": true, "created": []}, "c0"]]}"""
                    }

                    else -> {
                        """{"methodResponses": [["Email/changes", {"oldState": "e2", "newState": "e3",
                           "hasMoreChanges": false, "created": []}, "c0"]]}"""
                    }
                }
            }
            assertEquals(2, sent.size)
            assertTrue("\"sinceState\":\"e2\"" in sent.last(), sent.last())
            assertEquals(mapOf("ada" to "e3"), states.saved)
        }

    @Test
    fun aForgottenStateStartsAgainWithoutOldMail() =
        runTest {
            val states = States(mapOf("ada" to "old"))
            val shown =
                check(states) { body ->
                    if ("Email/changes" in body) {
                        """{"methodResponses": [["error", {"type": "cannotCalculateChanges"}, "c0"]]}"""
                    } else {
                        """{"methodResponses": [["Email/get", {"state": "e9", "list": []}, "c0"]]}"""
                    }
                }
            assertEquals(emptyList(), shown)
            assertEquals(mapOf("ada" to "e9"), states.saved)
        }

    @Test
    fun aFailedLoginKeepsItsState() =
        runTest {
            val states = States(mapOf("ada" to "e1"))
            val shown = check(states) { """{"methodResponses": [["error", {"type": "serverFail"}, "c0"]]}""" }
            assertEquals(emptyList(), shown)
            assertEquals(mapOf("ada" to "e1"), states.saved)
        }

    @Test
    fun unreadableLoginsLeaveEveryStateAsItIs() =
        runTest {
            val states = States(mapOf("ada" to "e1"))
            val locked =
                object : LoginStore by InMemoryLoginStore() {
                    override fun logins(): List<JmapLogin> = throw LoginStoreException("locked")
                }
            val alerts = Alerts()
            checker(JmapConnections(JmapClient(stalwartLike()), locked), states).check(alerts)
            assertEquals(emptyList(), alerts.shown)
            assertEquals(mapOf("ada" to "e1"), states.saved)
        }

    @Test
    fun bccAndListMailNamesTheAddressItWasDeliveredTo() =
        runTest {
            val shown =
                check(States(mapOf("ada" to "e1"))) { body ->
                    if ("Email/changes" in body) {
                        """{"methodResponses": [["Email/changes", {"oldState": "e1", "newState": "e2",
                           "hasMoreChanges": false, "created": ["m1"]}, "c0"]]}"""
                    } else {
                        assertTrue("header:Delivered-To:asAddresses:all" in body, body)
                        val headers =
                            """{"to": [{"email": "list@lists.example.org"}],
                               "header:Delivered-To:asAddresses:all": [[{"email": "ada+news@example.org"}],
                                 [{"email": "forged@example.org"}]],
                               "header:X-Original-To:asAddresses:all": [[{"email": "news@example.org"}]], "id""""
                        val delivered = email("m1", "i", "{}").replace("{\"id\"", headers)
                        """{"methodResponses": [
                           ["Mailbox/get", {"state": "b1", "list": [{"id": "i", "role": "inbox"}]}, "c0"],
                           ["Email/get", {"state": "e2", "list": [$delivered]}, "c1"]]}"""
                    }
                }
            assertEquals(
                listOf("list@lists.example.org", "ada+news@example.org", "news@example.org"),
                shown.single().recipients,
            )
        }

    @Test
    fun mailInAMutedConversationIsNotShown() =
        runTest {
            val states = States(mapOf("ada" to "e1"))
            val shown =
                check(states) { body ->
                    if ("Email/changes" in body) {
                        """{"methodResponses": [["Email/changes", {"oldState": "e1", "newState": "e2",
                           "hasMoreChanges": false, "created": ["m1", "m2"]}, "c0"]]}"""
                    } else {
                        assertTrue("\"Thread/get\"" in body, body)
                        """{"methodResponses": [
                           ["Mailbox/get", {"state": "b1", "list": [{"id": "i", "role": "inbox"}]}, "c0"],
                           ["Email/get", {"state": "e2", "list": [
                             ${email("m1", "i", "{}").replace("{\"id\"", "{\"threadId\": \"loud\", \"id\"")},
                             ${email("m2", "i", "{}").replace("{\"id\"", "{\"threadId\": \"quiet\", \"id\"")}]}, "c1"],
                           ["Thread/get", {"state": "t", "list": [{"id": "loud", "emailIds": ["m1"]},
                             {"id": "quiet", "emailIds": ["old", "m2"]}]}, "c2"],
                           ["Email/get", {"state": "e2", "list": [
                             {"id": "m1", "threadId": "loud", "keywords": {}, "receivedAt": "2026-10-14T08:00:00Z"},
                             {"id": "old", "threadId": "quiet", "keywords": {"${'$'}muted": true}, "receivedAt": "2026-10-01T08:00:00Z"},
                             {"id": "m2", "threadId": "quiet", "keywords": {}, "receivedAt": "2026-10-14T08:00:00Z"}]}, "c3"]]}"""
                    }
                }
            assertEquals(listOf("m1"), shown.map { it.emailId })
        }

    @Test
    fun mailReadOrDeletedElsewhereLosesItsAlert() =
        runTest {
            val alerts = Alerts(listOf("m1", "m2", "m3", "m4").map { AlertedMail("ada", it) }.toSet())
            check(States(mapOf("ada" to "e1")), alerts) { body ->
                if ("Email/changes" in body) {
                    """{"methodResponses": [["Email/changes", {"oldState": "e1", "newState": "e2",
                       "hasMoreChanges": false, "updated": ["m1", "m2", "m5"], "destroyed": ["m3"]}, "c0"]]}"""
                } else {
                    assertTrue("\"ids\":[\"m1\",\"m2\",\"m3\"]" in body, body)
                    """{"methodResponses": [
                       ["Mailbox/get", {"state": "b1", "list": [{"id": "i", "role": "inbox"}]}, "c0"],
                       ["Email/get", {"state": "e2", "notFound": ["m3"], "list": [
                         ${email("m1", "i", "{\"\$seen\": true}")}, ${email("m2", "i", "{}")}]}, "c1"]]}"""
                }
            }
            assertEquals(listOf("m1", "m3"), alerts.cleared.map { it.emailId })
        }

    @Test
    fun aForgottenStateLooksAtEveryAlert() =
        runTest {
            val alerts = Alerts(setOf(AlertedMail("ada", "m1"), AlertedMail("ada", "m2")))
            check(States(mapOf("ada" to "old")), alerts) { body ->
                when {
                    "Email/changes" in body -> {
                        """{"methodResponses": [["error", {"type": "cannotCalculateChanges"}, "c0"]]}"""
                    }

                    "Mailbox/get" in body -> {
                        """{"methodResponses": [
                           ["Mailbox/get", {"state": "b1", "list": [{"id": "i", "role": "inbox"}]}, "c0"],
                           ["Email/get", {"state": "e9", "list": [${email(
                            "m1",
                            "a",
                            "{}",
                        )}, ${email("m2", "i", "{}")}]}, "c1"]]}"""
                    }

                    else -> {
                        """{"methodResponses": [["Email/get", {"state": "e9", "list": []}, "c0"]]}"""
                    }
                }
            }
            assertEquals(listOf(AlertedMail("ada", "m1")), alerts.cleared)
        }

    private fun email(
        id: String,
        mailbox: String,
        keywords: String,
        name: String? = "Grace",
    ) = """{"id": "$id", "mailboxIds": {"$mailbox": true}, "keywords": $keywords,
            "from": [{${name?.let { "\"name\": \"$it\", " }.orEmpty()}"email": "grace@example.org"}],
            "subject": "Hello $id", "preview": "Preview", "receivedAt": "2026-10-14T08:00:00Z"}"""
}
