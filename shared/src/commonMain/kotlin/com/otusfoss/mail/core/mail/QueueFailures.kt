package com.otusfoss.mail.core.mail

import com.otusfoss.core.jmap.JmapException

/**
 * How a change queue treats a [JmapException]:
 * - [RetryLater]: no connection or a server that asks to be tried later. The change waits and the
 *   background job retries.
 * - [WaitForUser]: a rejected sign-in, forbidden access, or read-only account. The change waits
 *   for the user or next sync, and the background job does not retry.
 * - [Refused]: the server rejected this change or request. Drop or set it aside at once.
 * - [Unclear]: the server answered with an unclear failure. Pass over the change and set it aside
 *   after [QUEUE_MAX_TRIES] tries.
 */
internal enum class QueueFailure { RetryLater, WaitForUser, Refused, Unclear }

/**
 * Classifies [this] for a change queue. Only a missing connection, a sign-in or account block, or
 * a server that asks to be tried later keeps the queue waiting. A method error or client request
 * error refuses the call. Anything else is unclear.
 */
internal fun JmapException.queueFailure(): QueueFailure =
    when (this) {
        is JmapException.MethodError -> methodQueueFailure(type)
        is JmapException.RequestFailed -> requestQueueFailure(status)
        is JmapException.UploadTooLarge, is JmapException.Unsupported -> QueueFailure.Refused
        is JmapException.NotJmap, is JmapException.ResponseTooLarge -> QueueFailure.Unclear
        is JmapException.Unreachable -> QueueFailure.RetryLater
        else -> QueueFailure.WaitForUser
    }

private fun methodQueueFailure(type: String): QueueFailure =
    when (type) {
        SERVER_UNAVAILABLE -> QueueFailure.RetryLater
        ACCOUNT_READ_ONLY -> QueueFailure.WaitForUser
        SERVER_FAIL, SERVER_PARTIAL_FAIL -> QueueFailure.Unclear
        else -> QueueFailure.Refused
    }

private fun requestQueueFailure(status: Int): QueueFailure =
    when (status) {
        UNAUTHORIZED, FORBIDDEN_STATUS -> QueueFailure.WaitForUser
        REQUEST_TIMEOUT, TOO_MANY_REQUESTS -> QueueFailure.RetryLater
        in BAD_GATEWAY..GATEWAY_TIMEOUT -> QueueFailure.RetryLater
        in BAD_REQUEST..LAST_CLIENT_ERROR -> QueueFailure.Refused
        else -> QueueFailure.Unclear
    }

/** How often each queued key failed without a clear answer since the app started. */
internal class QueueTries(
    private val maxTries: Int = QUEUE_MAX_TRIES,
) {
    private var counts = emptyMap<String, Int>()

    /** Records one unclear failure for [key] and returns true once it has reached [maxTries]. */
    fun reachedLimit(key: String): Boolean {
        val count = (counts[key] ?: 0) + 1
        counts = counts + (key to count)
        return count >= maxTries
    }

    /** Clears recorded tries for [keys]. */
    fun reset(keys: Set<String>) {
        counts = counts - keys
    }

    /** Forgets tries for keys that are no longer in [keys]. */
    fun keep(keys: Set<String>) {
        counts = counts.filterKeys { it in keys }
    }
}

/** How many times a change may fail without a clear answer before the queue sets it aside. */
internal const val QUEUE_MAX_TRIES = 3

/** How many changes in a row may fail without a clear answer before a flush stops. */
internal const val QUEUE_MAX_UNCLEAR = 2

private const val SERVER_UNAVAILABLE = "serverUnavailable"
private const val ACCOUNT_READ_ONLY = "accountReadOnly"
private const val SERVER_FAIL = "serverFail"
private const val SERVER_PARTIAL_FAIL = "serverPartialFail"
private const val UNAUTHORIZED = 401
private const val FORBIDDEN_STATUS = 403
private const val REQUEST_TIMEOUT = 408
private const val TOO_MANY_REQUESTS = 429
private const val BAD_GATEWAY = 502
private const val GATEWAY_TIMEOUT = 504
private const val BAD_REQUEST = 400
private const val LAST_CLIENT_ERROR = 499
