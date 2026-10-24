package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapNewslettersTest {
    private val login = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")
    private val requests = mutableListOf<String>()
    private val posts = mutableListOf<HttpRequestData>()
    private val sent = mutableListOf<Draft>()
    private var postStatus = HttpStatusCode.OK

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
        val web =
            HttpClient(
                MockEngine { request ->
                    posts += request
                    respond("", postStatus)
                },
            )
        val drafts =
            FakeDrafts(
                listOf(
                    MailSender("Ada", "ada@alias.example.org", login.id),
                    MailSender("Ada", "ada@example.org", login.id),
                    MailSender("Ada", "ada@work.example.org", login.id),
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
        return JmapNewsletters(JmapConnections(JmapClient(engine), store), store, web, drafts, sender)
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
            val query = requests.last()
            assertTrue("\"header\":[\"List-Id\",\"weekly.sentry.io\"]" in query, query)
            assertTrue("\"before\":\"2026-10-15T08:00:00Z\"" in query && "\"limit\":100" in query, query)
        }

    @Test
    fun mailWithoutListHeadersIsNoList() =
        runTest {
            val plain =
                FIRST
                    .replace(", \"header:List-Id:asText\": \"Sentry weekly digest <weekly.sentry.io>\"", "")
                    .replace("\"header:List-Unsubscribe:asURLs\": [\"https://sentry.io/u/1\"]", "\"subject\": \"Hi\"")
            assertEquals(null, newsletters { plain }.list(newsletter))
            assertEquals(1, requests.size)
        }

    @Test
    fun oneClickPostsTheFormToTheListServer() =
        runTest {
            val how = Unsubscribe(oneClick = "https://sentry.io/u/1", mail = "mailto:leave@sentry.io")
            assertEquals(UnsubscribeResult.Done, newsletters().unsubscribe(newsletter, how))
            val post = posts.single()
            assertEquals(HttpMethod.Post, post.method)
            assertEquals("https://sentry.io/u/1", post.url.toString())
            val body = post.body as TextContent
            assertEquals("List-Unsubscribe=One-Click", body.text)
            assertEquals("application/x-www-form-urlencoded", body.contentType.toString())
            assertTrue(sent.isEmpty(), "No email when the POST worked")
        }

    @Test
    fun aRefusedPostFallsBackToTheMailtoAddress() =
        runTest {
            postStatus = HttpStatusCode.InternalServerError
            val how = Unsubscribe(oneClick = "https://sentry.io/u/1", mail = "mailto:leave@sentry.io?subject=stop")
            assertEquals(UnsubscribeResult.Done, newsletters().unsubscribe(newsletter, how))
            val draft = sent.single()
            assertEquals(listOf("leave@sentry.io"), draft.to.map { it.address })
            assertEquals("stop", draft.subject)
            assertEquals("ada@work.example.org", draft.from?.address, "Sent from the address the list writes to")
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
    fun withoutAKnownRecipientTheSignInAddressSendsNeverAnotherAlias() =
        runTest {
            val bcc = newsletter.copy(to = listOf(MailSender("Digest", "digest@sentry.io")))
            val how = Unsubscribe(mail = "mailto:leave@sentry.io")
            assertEquals(UnsubscribeResult.Done, newsletters().unsubscribe(bcc, how))
            assertEquals("ada@example.org", sent.single().from?.address)
        }

    @Test
    fun withoutPostOrMailTheUserFinishesOnTheWeb() =
        runTest {
            val newsletters = newsletters()
            assertEquals(
                UnsubscribeResult.OpenLink("http://sentry.io/u/1"),
                newsletters.unsubscribe(
                    newsletter,
                    Unsubscribe(oneClick = "http://sentry.io/u/1", web = "http://sentry.io/u/1"),
                ),
            )
            assertTrue(posts.isEmpty(), "Plain http never gets the POST")
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
