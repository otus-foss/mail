package com.otusfoss.mail.feature.read.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftSender
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.core.messages.domain.Unsubscribe
import com.otusfoss.mail.feature.read.domain.UnsubscribeResult
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.FakeDrafts
import com.otusfoss.mail.testing.emailWriter
import com.otusfoss.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JmapNewslettersTest {
    private val login = ADA_LOGIN
    private val requests = mutableListOf<String>()
    private val sent = mutableListOf<Draft>()

    private val newsletter =
        MailMessage(
            "ada/n5",
            MailSender("Sentry", "weekly@sentry.example"),
            "Your week",
            "",
            ReceivedAt("2026-10-15", "08:00"),
            MailLocation("Inbox", "Ada", "ada", "ada@example.org", mailboxId = "a", mailboxRole = MailboxRole.Inbox),
            emailId = "n5",
            to = listOf(MailSender("Ada", "ada@work.example.org")),
        )

    private fun newsletters(reply: (String) -> String = { error("no JMAP call expected: $it") }): JmapNewsletters {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val drafts =
            FakeDrafts(
                listOf(
                    MailSender("Ada", "ada@alias.example.org", login.id),
                    MailSender("Ada", "ada@example.org", login.id),
                    MailSender("Ada", "ada@work.example.org", login.id),
                    MailSender("Ada", "*@catch.example.org", login.id),
                ),
            )
        val sender =
            DraftSender { draft, _, _ ->
                sent += draft
                Result.success(SentMessage("ada", "s1", "sub1"))
            }
        val engine =
            stalwartLike { body ->
                requests += body
                reply(body)
            }
        val connections = JmapConnections(JmapClient(engine), store)
        val cache = InMemoryMailCache()
        return JmapNewsletters(connections, drafts, sender, emailWriter(connections, cache), Mailboxes(cache))
    }

    @Test
    fun aListFindsItsOlderMailByListIdAndHowOftenItSends() =
        runTest {
            val list =
                newsletters { body ->
                    if ("Email/query" in body) OLDER else FIRST
                }.list(newsletter)
            assertEquals("Sentry weekly digest", list?.name)
            assertEquals(listOf("n4", "n3"), list?.olderIds, "Mail in Trash or of another list does not count")
            assertEquals(1.0, list?.perWeek)
            assertEquals(2, requests.size)
            val query = requests.last()
            val filter =
                """{"operator":"AND","conditions":[{"header":["List-Id","weekly.sentry.example"]},""" +
                    """{"before":"2026-10-15T08:00:00Z"}]}"""
            assertTrue("\"filter\":$filter" in query, query)
            assertTrue("\"limit\":100" in query, query)
            assertTrue("\"from\",\"header:List-Id:asText\"]" in query, "The older mail is checked: $query")
        }

    @Test
    fun withoutAListIdOlderMailUsesTheFirstSenderAndOriginalTimestamp() =
        runTest {
            val first =
                WITHOUT_LIST_ID
                    .replace(
                        "\"email\": \"weekly@sentry.example\"}",
                        "\"email\": \"Weekly@Sentry.example\"}, {\"email\": \"other@example.org\"}",
                    ).replace("2026-10-15T08:00:00Z", "2026-10-15T08:00:00.000Z")
            val list =
                newsletters { body ->
                    if ("Email/query" in body) OLDER else first
                }.list(newsletter)
            assertEquals("Sentry", list?.name)
            assertEquals(listOf("n4", "n3"), list?.olderIds, "Mail only named after the sender does not count")
            assertEquals(1.0, list?.perWeek)
            assertEquals(2, requests.size)
            val query = requests.last()
            val filter =
                """{"operator":"AND","conditions":[{"from":"Weekly@Sentry.example"},""" +
                    """{"before":"2026-10-15T08:00:00.000Z"}]}"""
            assertTrue("\"filter\":$filter" in query, query)
            assertTrue("\"limit\":100" in query, query)
        }

    @Test
    fun withoutAListIdOrUsableFirstSenderNoOlderMailIsQueried() =
        runTest {
            listOf(
                "null",
                "[]",
                """[{"email":""}]""",
                """[{"email":" "}]""",
                """[{"email":" "},{"email":"other@example.org"}]""",
            ).forEach { from ->
                requests.clear()
                val first =
                    WITHOUT_LIST_ID.replace(
                        """[{"name": "Sentry", "email": "weekly@sentry.example"}]""",
                        from,
                    )
                val list = assertNotNull(newsletters { first }.list(newsletter), from)
                assertEquals("Sentry", list.name, from)
                assertTrue(list.olderIds.isEmpty(), from)
                assertNull(list.perWeek, from)
                assertEquals(1, requests.size, from)
            }
        }

    @Test
    fun mailWithoutListHeadersIsNoList() =
        runTest {
            val plain =
                WITHOUT_LIST_ID.replace(
                    "\"header:List-Unsubscribe:asURLs\": [\"https://sentry.example/u/1\"]",
                    "\"subject\": \"Hi\"",
                )
            assertEquals(null, newsletters { plain }.list(newsletter))
            assertEquals(1, requests.size)
        }

    @Test
    fun mailtoSendsFromTheAddressTheListReached() =
        runTest {
            val how = Unsubscribe(mail = "mailto:leave@sentry.example?subject=stop")
            assertEquals(UnsubscribeResult.Done, newsletters().unsubscribe(newsletter, how))
            val draft = sent.single()
            assertEquals(listOf("leave@sentry.example"), draft.to.map { it.address })
            assertEquals("stop", draft.subject)
            assertEquals("ada@work.example.org", draft.from?.address)
        }

    @Test
    fun aListThatWroteToABccAddressHearsFromTheAddressItReached() =
        runTest {
            val bcc = newsletter.copy(to = listOf(MailSender("Digest", "digest@sentry.example")))
            val how = Unsubscribe(mail = "mailto:leave@sentry.example", deliveredTo = listOf("Ada@Alias.example.org"))
            assertEquals(UnsubscribeResult.Done, newsletters().unsubscribe(bcc, how))
            assertEquals("ada@alias.example.org", sent.single().from?.address)
        }

    @Test
    fun withoutAKnownRecipientNoAddressOfTheUserIsRevealed() =
        runTest {
            val bcc = newsletter.copy(to = listOf(MailSender("Digest", "digest@sentry.example")))
            val newsletters = newsletters()
            val mailOnly = Unsubscribe(mail = "mailto:leave@sentry.example")
            assertEquals(UnsubscribeResult.Failed, newsletters.unsubscribe(bcc, mailOnly))
            val both = Unsubscribe(web = "https://sentry.example/u/1", mail = "mailto:leave@sentry.example")
            assertEquals(UnsubscribeResult.OpenLink("https://sentry.example/u/1"), newsletters.unsubscribe(bcc, both))
            assertTrue(sent.isEmpty(), "Neither the sign-in address nor another alias writes to the list: $sent")
        }

    @Test
    fun aCatchAllAliasUnsubscribesFromTheExactAddressTheListReached() =
        runTest {
            val shop = newsletter.copy(to = listOf(MailSender("Digest", "digest@sentry.example")))
            val how =
                Unsubscribe(mail = "mailto:leave@sentry.example", deliveredTo = listOf("Sentry@Catch.example.org"))
            assertEquals(UnsubscribeResult.Done, newsletters().unsubscribe(shop, how))
            assertEquals("sentry@catch.example.org", sent.single().from?.address)
        }

    @Test
    fun withoutMailTheUserFinishesOnTheWeb() =
        runTest {
            val newsletters = newsletters()
            assertEquals(
                UnsubscribeResult.OpenLink("http://sentry.example/u/1"),
                newsletters.unsubscribe(
                    newsletter,
                    Unsubscribe(web = "http://sentry.example/u/1"),
                ),
            )
            assertEquals(UnsubscribeResult.Failed, newsletters.unsubscribe(newsletter, Unsubscribe()))
        }

    @Test
    fun archivingMovesOlderMailOutOfEveryFolderIntoArchive() =
        runTest {
            val newsletters =
                newsletters { body ->
                    if ("Email/set" in body) {
                        """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {"n4": null, "n3": null}}, "c0"]]}"""
                    } else {
                        ARCHIVE_LOOKUP
                    }
                }
            assertTrue(newsletters.archive("ada", listOf("n4", "n3")))
            val set = requests.last()
            val n4 = "\"update\":{\"n4\":{\"mailboxIds/a\":null,\"mailboxIds/r\":null,\"mailboxIds/arch\":true}}"
            assertTrue(n4 in set, set)
            assertFalse("\"n3\"" in set, "n3 is already in Archive alone")
        }

    private companion object {
        const val FIRST = """{"methodResponses": [
            ["Mailbox/get", {"state": "m", "list": [{"id": "a", "role": "inbox"}, {"id": "t", "role": "trash"}]}, "c0"],
            ["Email/get", {"state": "e", "list": [{"id": "n5", "receivedAt": "2026-10-15T08:00:00Z",
              "from": [{"name": "Sentry", "email": "weekly@sentry.example"}],
              "header:List-Unsubscribe:asURLs": ["https://sentry.example/u/1"], "header:List-Id:asText": "Sentry weekly digest <weekly.sentry.example>"}]}, "c1"]]}"""
        val WITHOUT_LIST_ID =
            FIRST.replace(", \"header:List-Id:asText\": \"Sentry weekly digest <weekly.sentry.example>\"", "")
        const val SENTRY =
            """"from": [{"email": "weekly@sentry.example"}], """ +
                """"header:List-Id:asText": "<weekly.sentry.example>""""

        // The server matched y loosely: its list id holds Sentry's, and its sender's name is Sentry's address.
        const val OLDER = """{"methodResponses": [
            ["Email/query", {"queryState": "q", "ids": ["n4", "x", "y", "n3"]}, "c0"],
            ["Email/get", {"state": "e", "list": [
              {"id": "n4", "receivedAt": "2026-10-08T08:00:00Z", "mailboxIds": {"a": true}, $SENTRY},
              {"id": "x", "receivedAt": "2026-10-05T08:00:00Z", "mailboxIds": {"t": true}, $SENTRY},
              {"id": "y", "receivedAt": "2026-10-03T08:00:00Z", "mailboxIds": {"a": true},
               "from": [{"name": "weekly@sentry.example", "email": "news@shop.example"}],
               "header:List-Id:asText": "<news.weekly.sentry.example>"},
              {"id": "n3", "receivedAt": "2026-10-01T08:00:00Z", "mailboxIds": {"a": true}, $SENTRY}]}, "c1"]]}"""
        const val ARCHIVE_LOOKUP = """{"methodResponses": [
            ["Mailbox/get", {"state": "m", "list": [{"id": "a", "role": "inbox"}, {"id": "arch", "role": "archive"}]}, "c0"],
            ["Email/get", {"state": "e", "list": [
              {"id": "n4", "mailboxIds": {"a": true, "r": true}}, {"id": "n3", "mailboxIds": {"arch": true}}]}, "c1"]]}"""
    }
}
