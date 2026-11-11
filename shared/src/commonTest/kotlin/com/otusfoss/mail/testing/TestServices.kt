package com.otusfoss.mail.testing

import com.otusfoss.core.designsystem.shared.feedback.components.ToastState
import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.app.MailServices
import com.otusfoss.mail.core.messages.presentation.MailEdits
import com.otusfoss.mail.core.messages.presentation.MoveToasts
import com.otusfoss.mail.feature.inbox.presentation.InboxFeedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertTrue

/**
 * Services signed in to Ada's account alone, at a [stalwartLike] server that answers API calls
 * with [api]. Their JMAP calls and storage run on this test's scheduler, so [settle] finishes them.
 */
internal fun TestScope.adaServices(api: (String) -> String): MailServices {
    val scheduler = StandardTestDispatcher(testScheduler)
    return MailServices(
        logins = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") },
        createClient = { JmapClient(stalwartLike(STALWART_SESSION, emptyMap(), api, scheduler)) },
        io = scheduler,
    )
}

/** Edits saved in this scope that [feeds] show, with toasts whose work runs here too. */
internal fun CoroutineScope.testEdits(
    services: MailServices,
    vararg feeds: InboxFeedState,
): MailEdits {
    val edits = MailEdits(services.writer, this, MoveToasts(ToastState(), this))
    feeds.forEach { edits.observe(it.edits) }
    return edits
}

/** Runs the work that is due now, the JMAP calls of [adaServices] included, then checks [done]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.settle(done: () -> Boolean) {
    runCurrent()
    assertTrue(done(), "Pending work did not reach the expected state")
}
