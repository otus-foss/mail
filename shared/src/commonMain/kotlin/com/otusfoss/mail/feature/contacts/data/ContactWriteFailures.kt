package com.otusfoss.mail.feature.contacts.data

import com.otusfoss.core.jmap.JmapException
import com.otusfoss.mail.core.mail.QueueFailure
import com.otusfoss.mail.core.mail.queueFailure
import com.otusfoss.mail.feature.contacts.domain.ContactWriteException
import com.otusfoss.mail.feature.contacts.domain.ContactWriteFailure

/**
 * Why a change that failed with this error did not reach the server. Only a missing connection or
 * sign-in, or a server that asks to be tried later, is [ContactWriteFailure.Offline]: the change and
 * those after it wait. A method error or a request the server rejects as malformed or too large is
 * about this change alone, so it is refused. Anything else is [ContactWriteFailure.Failing].
 */
internal fun JmapException.writeFailure(): ContactWriteFailure =
    if (this is JmapException.MethodError && type == FORBIDDEN) {
        ContactWriteFailure.ReadOnly
    } else {
        when (queueFailure()) {
            QueueFailure.RetryLater, QueueFailure.WaitForUser -> ContactWriteFailure.Offline
            QueueFailure.Refused -> ContactWriteFailure.Refused
            QueueFailure.Unclear -> ContactWriteFailure.Failing
        }
    }

/** The failure of a SetError or method error of [type]: `forbidden` means the book is read-only now. */
internal fun refusal(type: String): ContactWriteFailure =
    if (type == FORBIDDEN) ContactWriteFailure.ReadOnly else ContactWriteFailure.Refused

internal fun refused(type: String): ContactWriteException = ContactWriteException(refusal(type))

private const val FORBIDDEN = "forbidden"
