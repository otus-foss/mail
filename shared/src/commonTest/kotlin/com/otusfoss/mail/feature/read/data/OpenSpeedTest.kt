package com.otusfoss.mail.feature.read.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.AccountMailCache
import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.CacheWrite
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.RecoveringMailCache
import com.otusfoss.mail.core.cache.withAccount
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.BodyPrefetch
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.MessageContent
import com.otusfoss.mail.core.messages.domain.NetworkCost
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.messages.domain.prefetch
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.DOWNLOAD_SESSION
import com.otusfoss.mail.testing.stalwartLike
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * How long an opened message waits for its body. The waits are real: a body that does not show
 * within [PATIENCE] while the cache is busy counts as stuck behind it.
 */
class OpenSpeedTest {
    private val login = ADA_LOGIN

    private val opened =
        MailMessage(
            "ada/e1",
            MailSender("Grace", "grace@example.org"),
            "Plans",
            "Short preview",
            ReceivedAt("2026-10-04", "09:30"),
            MailLocation("Inbox", "Ada", "ada", mailboxRole = MailboxRole.Inbox),
            emailId = "e1",
            preview = "Short preview",
        )

    private val requests = mutableListOf<String>()

    /** Bodies from a server that holds [sizes], each email's size in octets by id, and logs each request. */
    private fun bodies(
        cache: MailCache,
        sizes: Map<String, Long> = mapOf("e1" to SMALL),
    ): JmapMessageBodies {
        val api = { request: String ->
            requests += request
            val asked = sizes.keys.filter { "\"$it\"" in request }
            val sized = { id: String -> """{"id": "$id", "size": ${sizes[id]}}""" }
            val list = asked.map(if ("bodyValues" in request) ::email else sized)
            """{"methodResponses": [["Email/get", {"state": "e", "list": [${list.joinToString()}]}, "c0"]]}"""
        }
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val client = JmapClient(stalwartLike(DOWNLOAD_SESSION, emptyMap(), api))
        return JmapMessageBodies(JmapConnections(client, store), cache)
    }

    private fun bodyRequests(id: String): Int = requests.count { "bodyValues" in it && "\"$id\"" in it }

    /** Runs [test] on real threads against the real clock, since the waits it measures are real. */
    private fun realTime(test: suspend CoroutineScope.() -> Unit) = runTest { withContext(Dispatchers.Default, test) }

    private suspend fun JmapMessageBodies.firstContent(): MessageContent? =
        withTimeoutOrNull(PATIENCE) { content(opened).first() }

    @Test
    fun aCachedBodyOpensWhileAListSyncIsBeingSaved() =
        realTime {
            val storage = SlowWrites(InMemoryMailCache())
            val cache = AccountMailCache(RecoveringMailCache(storage))
            assertNotNull(bodies(cache).firstContent(), "The first open downloads the body")
            assertNotNull(withTimeoutOrNull(PATIENCE) { storage.saved.await() }, "The first open saves the body")
            storage.writing = CompletableDeferred()
            storage.hold = CompletableDeferred()
            val sync = launch { cache.withAccount("ada") { it.write("ada") { put(CacheKind.Email, "e2", "{}") } } }
            storage.writing.await()

            // The open runs on, so that giving up on it never waits for the write itself.
            val open = async { bodies(cache).content(opened).first() }
            val content = withTimeoutOrNull(PATIENCE) { open.await() }

            storage.hold?.complete(Unit)
            sync.join()
            open.join()
            assertEquals("Line one", content?.text, "A saved body must not wait for another write to finish")
        }

    @Test
    fun aDownloadedBodyShowsBeforeItIsSaved() =
        realTime {
            val storage = SlowWrites(InMemoryMailCache()).apply { hold = CompletableDeferred() }

            val open = async { bodies(AccountMailCache(RecoveringMailCache(storage))).content(opened).first() }
            val content = withTimeoutOrNull(PATIENCE) { open.await() }

            storage.hold?.complete(Unit)
            open.join()
            assertEquals("Line one", content?.text, "The body is on the device; saving it must not delay showing it")
        }

    @Test
    fun aDownloadedBodyIsStillSavedForTheNextOpen() =
        realTime {
            val storage = SlowWrites(InMemoryMailCache())
            val cache = AccountMailCache(RecoveringMailCache(storage))
            assertNotNull(bodies(cache).firstContent())

            val saved = withTimeoutOrNull(PATIENCE) { storage.saved.await() }

            assertNotNull(saved, "The body must reach the cache")
            assertEquals(setOf("e1"), storage.read("ada", CacheKind.Body, listOf("e1")).keys)
        }

    @Test
    fun aListedMessageOpensWithoutADownload() =
        realTime {
            val storage = SlowWrites(InMemoryMailCache())
            val bodies = bodies(AccountMailCache(RecoveringMailCache(storage)))

            bodies.prefetch(listOf(opened))
            assertNotNull(withTimeoutOrNull(PATIENCE) { storage.saved.await() }, "The listed body reaches the cache")
            requests.clear()
            val content = bodies.firstContent()

            assertEquals("Line one", content?.text)
            assertEquals(emptyList(), requests, "Opening a listed message must not ask the server")
        }

    @Test
    fun onlySmallBodiesComeAheadOfTheirOpening() =
        realTime {
            val storage = SlowWrites(InMemoryMailCache())
            val bodies = bodies(AccountMailCache(RecoveringMailCache(storage)), mapOf("e1" to SMALL, "e2" to LARGE))
            val large = opened.copy(id = "ada/e2", emailId = "e2")

            bodies.prefetch(listOf(large, opened))
            assertNotNull(withTimeoutOrNull(PATIENCE) { storage.saved.await() }, "The small body reaches the cache")

            assertEquals(1, bodyRequests("e1"), "The small body is fetched")
            assertEquals(0, bodyRequests("e2"), "A large body waits until it is opened")
        }

    @Test
    fun bodiesOnTheDeviceAreNotFetchedAgain() =
        realTime {
            val storage = SlowWrites(InMemoryMailCache())
            val bodies = bodies(AccountMailCache(RecoveringMailCache(storage)))
            assertNotNull(bodies.firstContent())
            assertNotNull(withTimeoutOrNull(PATIENCE) { storage.saved.await() })
            requests.clear()

            bodies.prefetch(listOf(opened))

            assertEquals(emptyList(), requests)
        }

    @Test
    fun theNeverSettingFetchesNothingAhead() =
        realTime {
            bodies(InMemoryMailCache()).prefetch(listOf(opened), BodyPrefetch.Never, Network())

            assertEquals(emptyList(), requests)
        }

    @Test
    fun dataSaverFetchesNothingAheadWhateverTheSetting() =
        realTime {
            bodies(InMemoryMailCache()).prefetch(listOf(opened), BodyPrefetch.Always, Network(dataSaver = true))

            assertEquals(emptyList(), requests)
        }

    @Test
    fun theUnmeteredSettingWaitsForANetworkThatIsNotMetered() =
        realTime {
            val bodies = bodies(InMemoryMailCache())

            bodies.prefetch(listOf(opened), BodyPrefetch.Unmetered, Network(metered = true))
            assertEquals(emptyList(), requests, "A metered network, such as mobile data, fetches nothing ahead")

            bodies.prefetch(listOf(opened), BodyPrefetch.Unmetered, Network(metered = false))
            assertEquals(1, bodyRequests("e1"), "A network that is not metered fetches the body")
        }

    private class Network(
        override val metered: Boolean = false,
        override val dataSaver: Boolean = false,
    ) : NetworkCost

    /** A cache whose writes wait for [hold], as a large list sync does inside its transaction. */
    private class SlowWrites(
        private val cache: MailCache,
    ) : MailCache by cache {
        var hold: CompletableDeferred<Unit>? = null
        var writing = CompletableDeferred<Unit>()
        val saved = CompletableDeferred<Unit>()

        override suspend fun write(
            loginId: String,
            changes: CacheWrite.() -> Unit,
        ) {
            writing.complete(Unit)
            hold?.await()
            cache.write(loginId, changes)
            saved.complete(Unit)
        }
    }

    private companion object {
        val PATIENCE = 3.seconds

        const val SMALL = 20_000L
        const val LARGE = 5_000_000L

        fun email(id: String) =
            """{"id": "$id", "receivedAt": "2026-10-04T09:30:00Z",
                "textBody": [{"partId": "1", "type": "text/plain"}],
                "htmlBody": [{"partId": "1", "type": "text/plain"}],
                "attachments": [],
                "bodyValues": {"1": {"value": "Line one"}}}"""
    }
}
