package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.testing.stalwartLike
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class JmapAddressBookTest {
    private val ada = JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada")

    // The fake server knows one password, so the second login signs in with the same address.
    private val bo = ada.copy(id = "bo", name = "Bo")
    private val store =
        InMemoryLoginStore().apply {
            save(ada, "secret")
            save(bo, "secret")
        }

    @Test
    fun oneSlowAccountDoesNotHoldUpAnothersSuggestions() =
        runTest {
            val stalled = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val first = Mutex()
            var asked = 0
            val server = stalwartLike(api = { MAIL })
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath == "/jmap/" && first.withLock { asked++ == 0 }) {
                        stalled.complete(Unit)
                        release.await()
                    }
                    server.config.requestHandlers.single()(this, request)
                }
            val book = JmapAddressBook(JmapConnections(JmapClient(engine), store))
            withContext(Dispatchers.Default) {
                val slow = async { book.suggestions("ada", "gr") }
                withTimeout(10.seconds) {
                    stalled.await()
                    assertEquals(listOf("grace@x.example"), book.suggestions("bo", "gr").map { it.address })
                }
                release.complete(Unit)
                assertEquals(listOf("grace@x.example"), slow.await().map { it.address })
            }
        }

    private companion object {
        const val MAIL = """{"methodResponses": [["Email/query", {"ids": ["e1"]}, "c0"],
            ["Email/get", {"list": [{"id": "e1", "receivedAt": "2026-10-18T09:00:00Z",
             "from": [{"name": "Grace", "email": "grace@x.example"}]}]}, "c1"]]}"""
    }
}
