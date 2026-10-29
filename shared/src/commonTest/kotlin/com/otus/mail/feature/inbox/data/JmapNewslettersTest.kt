package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.SentMessage
import com.otus.mail.feature.inbox.domain.Unsubscribe
import com.otus.mail.feature.inbox.domain.UnsubscribeResult
import com.otus.mail.feature.inbox.presentation.FakeDrafts
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
            MailSender("Sentry", "weekly@sentry.io"),
            "Your week",
            "",
            ReceivedAt("2026-10-15", "08:00"),
            MailLocation("Inbox", "Ada", "ada", "ada@example.org", mailboxId = "a"),
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
        return JmapNewsletters(JmapConnections(JmapClient(engine), store), store, drafts, sender)
    }

    @Test
    fun aListFindsItsOlderMailByListIdAndHowOftenItSends() =
        runTest {
            val list =
                newsletters { body ->
                    if ("Email/query" in body) OLDER else FIRST
                }.list(newsletter)
            assertEquals("Sentry weekly digest", list?.name)
            assertEquals(listOf("n4", "n3"), list?.olderIds, "Mail in Trash does not count")
            assertEquals(1.0, list?.perWeek)
            assertEquals(2, requests.size)
            val query = requests.last()
            val filter =
                """{"operator":"AND","conditions":[{"header":["List-Id","weekly.sentry.io"]},""" +
                    """{"before":"2026-10-15T08:00:00Z"}]}"""
            assertTrue("\"filter\":$filter" in query, query)
            assertTrue("\"limit\":100" in query, query)
        }

    @Test
    fun withoutAListIdOlderMailUsesTheFirstSenderAndOriginalTimestamp() =
        runTest {
            val first =
                WITHOUT_LIST_ID
                    .replace(
                        "\"email\": \"weekly@sentry.io\"}",
                        "\"email\": \"Weekly@Sentry.io\"}, {\"email\": \"other@example.org\"}",
                    ).replace("2026-10-15T08:00:00Z", "2026-10-15T08:00:00.000Z")
            val list =
                newsletters { body ->
                    if ("Email/query" in body) OLDER else first
                }.list(newsletter)
            assertEquals("Sentry", list?.name)
            assertEquals(listOf("n4", "n3"), list?.olderIds)
            assertEquals(1.0, list?.perWeek)
            assertEquals(2, requests.size)
            val query = requests.last()
            val filter =
                """{"operator":"AND","conditions":[{"from":"Weekly@Sentry.io"},""" +
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
                        """[{"name": "Sentry", "email": "weekly@sentry.io"}]""",
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
                    "\"header:List-Unsubscribe:asURLs\": [\"https://sentry.io/u/1\"]",
                    "\"subject\": \"Hi\"",
                )
            assertEquals(null, newsletters { plain }.list(newsletter))
            assertEquals(1, requests.size)
        }

    @Test
    fun mailtoSendsFromTheAddressTheListReached() =
        runTest {
            val how = Unsubscribe(mail = "mailto:leave@sentry.io?subject=stop")
            assertEquals(UnsubscribeResult.Done, newsletters().unsubscribe(newsletter, how))
            val draft = sent.single()
            assertEquals(listOf("leave@sentry.io"), draft.to.map { it.address })
            assertEquals("stop", draft.subject)
            assertEquals("ada@work.example.org", draft.from?.address)
        }

    @Test
    fun aListThatWroteToABccAddressHearsFromTheAddressItReached() =
        runTest {
            val bcc = newsletter.copy(to = listOf(MailSender("Digest", "digest@sentry.io")))
            val how = Unsubscribe(mail = "mailto:leave@sentry.io", deliveredTo = listOf("Ada@Alias.example.org"))
            assertEquals(UnsubscribeResult.Done, newsletters().unsubscribe(bcc, how))
            assertEquals("ada@alias.example.org", sent.single().from?.address)
        }

    @Test
    fun withoutAKnownRecipientNoAddressOfTheUserIsRevealed() =
        runTest {
            val bcc = newsletter.copy(to = listOf(MailSender("Digest", "digest@sentry.io")))
            val newsletters = newsletters()
            val mailOnly = Unsubscribe(mail = "mailto:leave@sentry.io")
            assertEquals(UnsubscribeResult.Failed, newsletters.unsubscribe(bcc, mailOnly))
            val both = Unsubscribe(web = "https://sentry.io/u/1", mail = "mailto:leave@sentry.io")
            assertEquals(UnsubscribeResult.OpenLink("https://sentry.io/u/1"), newsletters.unsubscribe(bcc, both))
            assertTrue(sent.isEmpty(), "Neither the sign-in address nor another alias writes to the list: $sent")
        }

    @Test
    fun aCatchAllAliasUnsubscribesFromTheExactAddressTheListReached() =
        runTest {
            val shop = newsletter.copy(to = listOf(MailSender("Digest", "digest@sentry.io")))
            val how = Unsubscribe(mail = "mailto:leave@sentry.io", deliveredTo = listOf("Sentry@Catch.example.org"))
            assertEquals(UnsubscribeResult.Done, newsletters().unsubscribe(shop, how))
            assertEquals("sentry@catch.example.org", sent.single().from?.address)
        }

    @Test
    fun withoutMailTheUserFinishesOnTheWeb() =
        runTest {
            val newsletters = newsletters()
            assertEquals(
                UnsubscribeResult.OpenLink("http://sentry.io/u/1"),
                newsletters.unsubscribe(
                    newsletter,
                    Unsubscribe(web = "http://sentry.io/u/1"),
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
            assertTrue("\"mailboxIds/a\":null" in set && "\"mailboxIds/r\":null" in set, set)
            assertTrue("\"mailboxIds/arch\":true" in set, set)
        }

    private companion object {
        const val FIRST = """{"methodResponses": [
            ["Email/get", {"state": "e", "list": [{"id": "n5", "receivedAt": "2026-10-15T08:00:00Z",
              "from": [{"name": "Sentry", "email": "weekly@sentry.io"}],
              "header:List-Unsubscribe:asURLs": ["https://sentry.io/u/1"], "header:List-Id:asText": "Sentry weekly digest <weekly.sentry.io>"}]}, "c0"],
            ["Mailbox/get", {"state": "m", "list": [{"id": "a", "role": "inbox"}, {"id": "t", "role": "trash"}]}, "c1"]]}"""
        val WITHOUT_LIST_ID =
            FIRST.replace(", \"header:List-Id:asText\": \"Sentry weekly digest <weekly.sentry.io>\"", "")
        const val OLDER = """{"methodResponses": [
            ["Email/query", {"queryState": "q", "ids": ["n4", "x", "n3"]}, "c0"],
            ["Email/get", {"state": "e", "list": [
              {"id": "n4", "receivedAt": "2026-10-08T08:00:00Z", "mailboxIds": {"a": true}},
              {"id": "x", "receivedAt": "2026-10-05T08:00:00Z", "mailboxIds": {"t": true}},
              {"id": "n3", "receivedAt": "2026-10-01T08:00:00Z", "mailboxIds": {"a": true}}]}, "c1"]]}"""
        const val ARCHIVE_LOOKUP = """{"methodResponses": [
            ["Mailbox/query", {"queryState": "q", "ids": ["arch"]}, "c0"],
            ["Email/get", {"state": "e", "list": [
              {"id": "n4", "mailboxIds": {"a": true, "r": true}}, {"id": "n3", "mailboxIds": {"arch": true}}]}, "c1"]]}"""
    }
}
