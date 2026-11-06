package com.otus.mail.feature.compose.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.feature.inbox.data.composeMail
import com.otus.mail.feature.inbox.data.id
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.SUBMISSION_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Holding mail for later needs `maxDelayedSend` and the FUTURERELEASE submission extension. */
class DelayedSendTest {
    private suspend fun delay(submission: String): Long {
        val session =
            SUBMISSION_SESSION.replace(
                "\"urn:ietf:params:jmap:submission\": {}}}}",
                "\"urn:ietf:params:jmap:submission\": $submission}}}",
            )
        check(session != SUBMISSION_SESSION)
        val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
        val sender =
            composeMail(JmapConnections(JmapClient(stalwartLike(session)), store)).sender
        return sender.maxDelaySeconds(ADA_LOGIN.id)
    }

    @Test
    fun aServerThatOffersFutureReleaseHoldsMailForItsMaximumDelay() =
        runTest {
            val held = """{"maxDelayedSend": 2592000, "submissionExtensions": {"FUTURERELEASE": [], "SIZE": []}}"""
            assertEquals(2_592_000, delay(held))
        }

    @Test
    fun withoutFutureReleaseNothingIsHeldWhateverTheDelaySays() =
        runTest {
            assertEquals(0, delay("""{"maxDelayedSend": 2592000, "submissionExtensions": {"SIZE": []}}"""))
            assertEquals(0, delay("""{"maxDelayedSend": 2592000}"""))
        }
}
