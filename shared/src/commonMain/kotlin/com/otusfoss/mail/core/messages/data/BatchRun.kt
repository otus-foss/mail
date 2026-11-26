package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.QUEUE_MAX_UNCLEAR
import com.otusfoss.mail.core.mail.QueueFailure
import com.otusfoss.mail.core.mail.QueueTries
import com.otusfoss.mail.core.mail.queueFailure
import kotlinx.serialization.json.JsonObject

/** Outcome of sending one login's waiting changes. */
internal class BatchRun(
    var left: Map<String, JsonObject>,
) {
    var refused: Set<String> = emptySet()
    var limited: Boolean = false
    var retry: Boolean = false
    var unclearInARow: Int = 0
    var stop: Boolean = false

    /** Whether the server took a change in this run, which shows that it works. */
    var answered: Boolean = false

    /** Changes that failed without a clear answer, which count as tried only when [answered]. */
    var unclear: List<String> = emptyList()

    fun halt(retry: Boolean) {
        this.retry = retry
        stop = true
    }
}

/**
 * Sends [waiting] to [account] in batches of its `setBatch` size. A batch that fails as a whole
 * with a refusal or unclear error falls back to one change at a time so one bad email does not
 * block the rest.
 */
internal suspend fun sendBatches(
    account: MailAccount,
    waiting: Map<String, JsonObject>,
    tries: QueueTries,
): BatchRun {
    val run = BatchRun(left = waiting)
    for (batch in waiting.entries.chunked(account.session.setBatch)) {
        if (run.stop) break
        sendChunk(account, batch.associate { it.toPair() }, tries, run)
    }
    // Only a server that works shows the fault is in these changes; one that fails every call says
    // nothing about them, so they wait without a try. An empty Email/set asks when nothing else went.
    if (run.unclear.isNotEmpty() && !run.answered) run.answered = account.takesEmailSet()
    if (run.answered) {
        val dropped = run.unclear.filter(tries::reachedLimit)
        run.left = run.left - dropped
        run.refused = run.refused + dropped
    }
    if (run.left.keys.any { it in run.unclear }) run.retry = true
    return run
}

private suspend fun sendChunk(
    account: MailAccount,
    chunk: Map<String, JsonObject>,
    tries: QueueTries,
    run: BatchRun,
) {
    try {
        val result = account.connection.call(SetMethod(EmailType, account.id, update = chunk))
        val batchRefused = result.notUpdated.filterValues { it.type !in TEMPORARY_SET_ERRORS }.keys
        val answered = result.updated.keys + batchRefused
        val rateLimited = answered.size < chunk.size
        run.limited = run.limited || rateLimited
        run.retry = run.retry || rateLimited
        run.left = run.left - answered
        run.refused = run.refused + batchRefused
        run.unclearInARow = 0
        run.answered = true
    } catch (error: JmapException) {
        handleFailure(account, chunk, error.queueFailure(), tries, run)
    }
}

private suspend fun handleFailure(
    account: MailAccount,
    chunk: Map<String, JsonObject>,
    failure: QueueFailure,
    tries: QueueTries,
    run: BatchRun,
) {
    when (failure) {
        QueueFailure.RetryLater -> run.halt(retry = true)
        QueueFailure.WaitForUser -> run.halt(retry = false)
        QueueFailure.Refused, QueueFailure.Unclear -> retryOrDrop(account, chunk, failure, tries, run)
    }
}

private suspend fun retryOrDrop(
    account: MailAccount,
    chunk: Map<String, JsonObject>,
    failure: QueueFailure,
    tries: QueueTries,
    run: BatchRun,
) {
    if (chunk.size > 1) {
        for ((emailId, patch) in chunk) {
            if (run.stop) break
            sendChunk(account, mapOf(emailId to patch), tries, run)
        }
        return
    }
    val emailId = chunk.keys.single()
    if (failure == QueueFailure.Refused) {
        run.left = run.left - emailId
        run.refused = run.refused + emailId
        run.unclearInARow = 0
        return
    }
    run.unclear = run.unclear + emailId
    run.unclearInARow += 1
    if (run.unclearInARow >= QUEUE_MAX_UNCLEAR) run.stop = true
}

/** Whether the server answers an `Email/set` that changes nothing, so it works for other changes. */
private suspend fun MailAccount.takesEmailSet(): Boolean =
    try {
        connection.call(SetMethod(EmailType, id, update = emptyMap()))
        true
    } catch (expected: JmapException) {
        false
    }

/** `SetError` types that may not recur when the same change is sent again later. */
private val TEMPORARY_SET_ERRORS = setOf("rateLimit")
