package com.otus.mail.app

import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.jmap.JmapClient
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.messages.presentation.MailEdits
import com.otus.mail.core.messages.presentation.MoveToasts
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.testing.ADA_LOGIN
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext

/** Services signed in to Ada's account alone, on the server that [engine] mocks. */
internal fun adaServices(engine: HttpClientEngine): MailServices =
    MailServices(
        logins = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") },
        createClient = { JmapClient(engine) },
    )

/** Edits saved in this scope that [feeds] show, with toasts whose work runs here too. */
internal fun CoroutineScope.testEdits(
    services: MailServices,
    vararg feeds: InboxFeedState,
): MailEdits {
    val edits = MailEdits(services.writer, this, MoveToasts(ToastState(), this))
    feeds.forEach { edits.observe(it.edits) }
    return edits
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

private const val SETTLE_STEPS = 50
