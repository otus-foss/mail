package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.SUBMISSION_SESSION
import com.otusfoss.mail.testing.composeMail
import com.otusfoss.mail.testing.stalwartLike
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
