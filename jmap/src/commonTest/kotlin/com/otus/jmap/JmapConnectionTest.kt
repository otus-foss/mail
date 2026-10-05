package com.otus.jmap

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class JmapConnectionTest {
    private var sessionFetches = 0
    private var serverState = "s1"
    private var rejectApi = false

    private val engine =
        MockEngine { request ->
            when (request.url.encodedPath) {
                "/jmap/session" -> {
                    sessionFetches++
                    json(session(state = serverState))
                }

                else -> {
                    if (rejectApi) {
                        respond("", HttpStatusCode.Unauthorized)
                    } else {
                        json("""{"methodResponses": [], "sessionState": "$serverState"}""")
                    }
                }
            }
        }
    private val connection = JmapConnection(JmapClient(engine), "https://mail.example.org/jmap/session", ADA)

    @Test
    fun theSessionIsCachedUntilTheServerReportsAChange() =
        runTest {
            connection.send {}
            connection.send {}
            assertEquals(1, sessionFetches)
            serverState = "s2"
            connection.send {}
            connection.send {}
            assertEquals(2, sessionFetches, "A new sessionState refetches the session once")
            assertEquals("s2", connection.session().state)
        }

    @Test
    fun aFailedRequestDropsTheSession() =
        runTest {
            connection.send {}
            rejectApi = true
            assertFailsWith<JmapException.Unauthorized> { connection.send {} }
            rejectApi = false
            connection.send {}
            assertEquals(2, sessionFetches)
        }

    @Test
    fun connectionsAreSharedPerLoginAndNeedASecret() =
        runTest {
            val store = InMemoryLoginStore()
            val login = JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada")
            val connections = JmapConnections(JmapClient(engine), store)
            assertFailsWith<JmapException.CredentialsUnavailable> { connections.connection(login) }
            store.save(login, "secret")
            val first = connections.connection(login)
            assertSame(first, connections.connection(login))
            connections.forget(login.id)
            assertEquals(false, first === connections.connection(login), "A replaced login gets a new connection")
        }
}
