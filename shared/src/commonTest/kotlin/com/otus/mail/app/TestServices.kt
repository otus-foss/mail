package com.otus.mail.app

import com.otus.jmap.JmapClient
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext

/** Services signed in to Ada's account alone, on the server that [engine] mocks. */
internal fun adaServices(engine: HttpClientEngine): MailServices {
    val logins = InMemoryLoginStore().apply { save(JmapLogin("ada", ADA_URL, "ada@example.org", "Ada"), "secret") }
    return MailServices(logins = logins, createClient = { JmapClient(engine) })
}

/** Lets the JMAP calls, which read logins off the test thread, finish until [done], or for a short while. */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.settle(done: () -> Boolean) {
    repeat(SETTLE_STEPS) {
        runCurrent()
        if (done()) return
        withContext(Dispatchers.Default) { delay(10) }
    }
}

private const val ADA_URL = "https://mail.example.org/.well-known/jmap"
private const val SETTLE_STEPS = 50
