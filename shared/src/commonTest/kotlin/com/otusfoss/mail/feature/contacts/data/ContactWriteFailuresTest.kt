package com.otusfoss.mail.feature.contacts.data

import com.otusfoss.core.jmap.JmapException
import com.otusfoss.mail.feature.contacts.domain.ContactWriteFailure
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactWriteFailuresTest {
    @Test
    fun noConnectionSignInOrBusyServerWaits() {
        val offline =
            listOf(
                JmapException.Unreachable(IllegalStateException("timeout")),
                JmapException.Unauthorized(),
                JmapException.CredentialsUnavailable("ada"),
                JmapException.RequestFailed(status = 429, type = null),
                JmapException.RequestFailed(status = 503, type = null),
                JmapException.RequestFailed(400, "urn:ietf:params:jmap:error:limit", "maxConcurrentRequests"),
                JmapException.NotJmap("a captive portal's page"),
            )
        assertEquals(List(offline.size) { ContactWriteFailure.Offline }, offline.map { it.writeFailure() })
    }

    @Test
    fun aRequestTheServerRejectsIsRefused() {
        val notRequest = JmapException.RequestFailed(status = 400, type = "urn:ietf:params:jmap:error:notRequest")
        assertEquals(ContactWriteFailure.Refused, notRequest.writeFailure())
        assertEquals(ContactWriteFailure.Refused, JmapException.MethodError("invalidArguments", null).writeFailure())
        assertEquals(ContactWriteFailure.ReadOnly, JmapException.MethodError("forbidden", null).writeFailure())
    }

    @Test
    fun anAnswerThatSaysNothingClearIsFailing() {
        assertEquals(ContactWriteFailure.Failing, JmapException.RequestFailed(status = 500, type = null).writeFailure())
    }
}
