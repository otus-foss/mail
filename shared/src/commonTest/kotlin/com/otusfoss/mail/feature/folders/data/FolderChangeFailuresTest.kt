package com.otusfoss.mail.feature.folders.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.folders.domain.FolderChangeStore
import com.otusfoss.mail.feature.folders.domain.FolderOutcome
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.MemoryMapStore
import com.otusfoss.mail.testing.STALWART_SESSION
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A folder change made offline outlasts one unclear or busy answer and goes once the server takes it. */
class FolderChangeFailuresTest {
    private val logins = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    private val cache = InMemoryMailCache()
    private val map = MemoryMapStore()
    private val sets = mutableListOf<String>()
    private var down = false
    private var failure: Pair<HttpStatusCode, String>? = null
    private val repository = repository()

    private fun repository(): JmapFolderRepository {
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    addHandler { request ->
                        val json = headersOf(HttpHeaders.ContentType, "application/json")
                        when (request.url.encodedPath) {
                            "/.well-known/jmap" -> {
                                respond(
                                    "",
                                    HttpStatusCode.TemporaryRedirect,
                                    headersOf(HttpHeaders.Location, "/jmap/session"),
                                )
                            }

                            "/jmap/session" -> {
                                respond(STALWART_SESSION, HttpStatusCode.OK, json)
                            }

                            else -> {
                                val (status, body) = answer((request.body as TextContent).text)
                                respond(body, status, json)
                            }
                        }
                    }
                },
            )
        val connections = JmapConnections(JmapClient(engine), logins)
        val mailboxes = Mailboxes(cache)
        val waiting =
            WaitingFolderChanges(
                connections,
                mailboxes,
                DeviceStore(map, FolderChangeStore),
                {},
                Dispatchers.Unconfined,
            )
        return JmapFolderRepository(connections, mailboxes, waiting)
    }

    private fun answer(body: String): Pair<HttpStatusCode, String> {
        if (down) throw IOException("The network is down")
        if ("Mailbox/set" !in body) return HttpStatusCode.OK to MAILBOXES
        sets += body
        return failure ?: (HttpStatusCode.OK to UPDATED)
    }

    @Test
    fun aRenameMadeOfflineOutlastsOneHttp500() =
        runTest {
            assertTheRenameOutlasts(HttpStatusCode.InternalServerError to "")
        }

    @Test
    fun aRenameMadeOfflineOutlastsAConcurrencyLimit() =
        runTest {
            assertTheRenameOutlasts(HttpStatusCode.BadRequest to LIMIT)
        }

    private suspend fun assertTheRenameOutlasts(failed: Pair<HttpStatusCode, String>) {
        val folder = requireNotNull(repository.accounts().single().folder("r"))
        down = true
        assertEquals(FolderOutcome.Done("r"), repository.rename(folder, "Bills"))
        down = false

        failure = failed
        repository.accounts()
        failure = null
        repository.accounts()

        assertEquals(2, sets.size, "The rename went again after the failed call")
        assertTrue("\"name\":\"Bills\"" in sets.last(), sets.last())
        assertEquals(emptyMap(), map.read(), "Nothing waits any more")
    }

    private companion object {
        const val MAILBOXES =
            """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
               {"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"}]}, "c0"]]}"""
        const val UPDATED =
            """{"methodResponses": [["Mailbox/set", {"newState": "m2", "updated": {"r": null}}, "c0"]]}"""
        const val LIMIT =
            """{"type": "urn:ietf:params:jmap:error:limit", "limit": "maxConcurrentRequests", "status": 400}"""
    }
}
