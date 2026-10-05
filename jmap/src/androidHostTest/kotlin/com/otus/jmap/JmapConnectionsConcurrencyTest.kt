package com.otus.jmap

import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginSecret
import com.otus.jmap.login.LoginStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JmapConnectionsConcurrencyTest {
    private val login = JmapLogin("a", "https://mail.example.org/session", "ada", "Ada")

    @Test
    fun invalidationWaitsForCreationAndPreventsStaleCredentialsFromBeingCached() =
        runBlocking {
            val store = BlockingSecrets()
            val headers = mutableListOf<String?>()
            val client =
                JmapClient(
                    MockEngine { request ->
                        headers += request.headers[HttpHeaders.Authorization]
                        json(session())
                    },
                )
            val connections = JmapConnections(client, store)
            val first = async(Dispatchers.Default) { connections.connection(login) }
            try {
                assertTrue(store.entered.await(5, TimeUnit.SECONDS))
                store.value = "new"
                val invalidation = async(start = CoroutineStart.UNDISPATCHED) { connections.forget(login.id) }
                store.release.countDown()
                val old = first.await()
                invalidation.await()
                val fresh = connections.connection(login)
                assertNotSame(old, fresh)
                fresh.session()
                assertEquals(JmapAuth.Basic("ada", "new").header(), headers.single())
            } finally {
                store.release.countDown()
            }
        }

    @Test
    fun concurrentCallersShareOneConnection() =
        runBlocking {
            val store = BlockingSecrets()
            val connections = JmapConnections(JmapClient(MockEngine { json(session()) }), store)
            val first = async(Dispatchers.Default) { connections.connection(login) }
            try {
                assertTrue(store.entered.await(5, TimeUnit.SECONDS))
                val second = async(start = CoroutineStart.UNDISPATCHED) { connections.connection(login) }
                store.release.countDown()
                assertSame(first.await(), second.await())
            } finally {
                store.release.countDown()
            }
        }
}

private class BlockingSecrets : LoginStore {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)

    @Volatile var value = "old"

    override fun secret(id: String): LoginSecret {
        val captured = value
        entered.countDown()
        check(release.await(5, TimeUnit.SECONDS))
        return LoginSecret.Found(captured)
    }

    override fun logins(): List<JmapLogin> = emptyList()

    override fun save(
        login: JmapLogin,
        secret: String,
    ) = Unit

    override fun remove(id: String) = Unit
}
