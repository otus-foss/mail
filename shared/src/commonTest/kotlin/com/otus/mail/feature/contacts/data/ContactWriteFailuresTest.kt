package com.otus.mail.feature.contacts.data

import com.otus.jmap.JmapException
import com.otus.mail.feature.contacts.domain.ContactWriteFailure
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactWriteFailuresTest {
    @Test
    fun noConnectionOrSignInWaits() {
        val offline =
            listOf(
                JmapException.Unreachable(IllegalStateException("timeout")),
                JmapException.Unauthorized(),
                JmapException.CredentialsUnavailable("ada"),
                JmapException.RequestFailed(status = 429, type = null),
                JmapException.RequestFailed(status = 503, type = null),
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
        assertEquals(ContactWriteFailure.Failing, JmapException.NotJmap("no methodResponses").writeFailure())
    }
}
