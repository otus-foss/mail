package com.otus.mail.feature.contacts.data

import com.otus.jmap.JmapException
import com.otus.mail.feature.contacts.domain.ContactWriteException
import com.otus.mail.feature.contacts.domain.ContactWriteFailure

/**
 * Why a change that failed with this error did not reach the server. Only a missing connection or
 * sign-in, or a server that asks to be tried later, is [ContactWriteFailure.Offline]: the change and
 * those after it wait. A method error or a request the server rejects as malformed or too large is
 * about this change alone, so it is refused. Anything else is [ContactWriteFailure.Failing].
 */
internal fun JmapException.writeFailure(): ContactWriteFailure =
    when (this) {
        is JmapException.MethodError -> refusal(type)
        is JmapException.RequestFailed -> requestFailure(status)
        is JmapException.UploadTooLarge, is JmapException.Unsupported -> ContactWriteFailure.Refused
        is JmapException.NotJmap, is JmapException.ResponseTooLarge -> ContactWriteFailure.Failing
        else -> ContactWriteFailure.Offline
    }

/**
 * A request-level problem by its HTTP [status] (RFC 8620, section 3.6.1). A sign-in problem, a
 * timeout, a rate limit, or an unavailable gateway passes. Another 4xx, such as `notRequest` or a
 * limit, rejects what was sent. An internal error may come from this card or from the server.
 */
private fun requestFailure(status: Int): ContactWriteFailure =
    when (status) {
        UNAUTHORIZED, FORBIDDEN_STATUS, REQUEST_TIMEOUT, TOO_MANY_REQUESTS -> ContactWriteFailure.Offline
        in BAD_GATEWAY..GATEWAY_TIMEOUT -> ContactWriteFailure.Offline
        in BAD_REQUEST..LAST_CLIENT_ERROR -> ContactWriteFailure.Refused
        else -> ContactWriteFailure.Failing
    }

/** The failure of a SetError or method error of [type]: `forbidden` means the book is read-only now. */
internal fun refusal(type: String): ContactWriteFailure =
    if (type == FORBIDDEN) ContactWriteFailure.ReadOnly else ContactWriteFailure.Refused

internal fun refused(type: String): ContactWriteException = ContactWriteException(refusal(type))

private const val FORBIDDEN = "forbidden"
private const val UNAUTHORIZED = 401
private const val FORBIDDEN_STATUS = 403
private const val REQUEST_TIMEOUT = 408
private const val TOO_MANY_REQUESTS = 429
private const val BAD_GATEWAY = 502
private const val GATEWAY_TIMEOUT = 504
private const val BAD_REQUEST = 400
private const val LAST_CLIENT_ERROR = 499
