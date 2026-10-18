package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.JmapRequestBuilder
import com.otus.jmap.JmapResponse
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.CacheWrite
import com.otus.mail.core.mail.EMAIL_PROPERTIES
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.ThreadObject
import com.otus.mail.core.mail.ThreadType
import com.otus.mail.core.mail.putObject
import kotlin.time.Instant

/**
 * What a list still needs from the server: [rows] to read with every row property, [threads] of
 * rows the device already holds, and thread [members] to read with only what a row counts.
 */
internal class WantedMail(
    val rows: Set<String>,
    val threads: Set<String> = emptySet(),
    val members: Set<String> = emptySet(),
)

/** Row emails, threads, and thread member emails the server sent. */
internal class FetchedMail(
    val rows: List<EmailObject> = emptyList(),
    val threads: List<ThreadObject> = emptyList(),
    val members: List<EmailObject> = emptyList(),
)

/**
 * Reads [wanted] in as few requests as the server allows (RFC 8621, sections 3 and 4.2). Each
 * `Email/get` of rows chains `Thread/get` of their threads and an `Email/get` of those threads'
 * emails by back-reference, so new rows arrive with their conversations. When the server refuses
 * a thread call, for example because a thread holds more emails than one `/get` may return, those
 * rows stand for their own emails.
 */
internal suspend fun JmapConnection.fetchMail(
    accountId: String,
    wanted: WantedMail,
): FetchedMail {
    val chunk = session().coreLimit("maxObjectsInGet", SUGGESTED_MAX_OBJECTS)
    val groups =
        wanted.rows.chunked(chunk).map { rowGroup(accountId, it) } +
            wanted.threads.chunked(chunk).map { threadGroup(accountId, it) } +
            wanted.members.chunked(chunk).map { memberGroup(accountId, it) }
    val parts = if (groups.isEmpty()) emptyList() else sendAll(groups)
    return FetchedMail(
        rows = parts.flatMap { it.rows },
        threads = parts.flatMap { it.threads },
        members = parts.flatMap { it.members },
    )
}

/** Saves [fetched] for the list, each email under its received time so old mail can be trimmed. */
internal fun CacheWrite.saveMail(fetched: FetchedMail) {
    fetched.rows.forEach { putObject(CacheKind.Email, it.id, EmailObject.serializer(), it, it.receivedMillis) }
    fetched.members.forEach { putObject(CacheKind.Member, it.id, EmailObject.serializer(), it, it.receivedMillis) }
    fetched.threads.forEach { putObject(CacheKind.Thread, it.id, ThreadObject.serializer(), it) }
}

private fun rowGroup(
    accountId: String,
    ids: List<String>,
): CallGroup<FetchedMail> =
    CallGroup(size = ROW_CALLS) {
        val rows = call(GetMethod(EmailType, accountId, JmapIds.Of(ids), EMAIL_PROPERTIES))
        val readThreads = threadCalls(accountId, JmapIds.Result(rows.resultOf("/list/*/threadId")))
        return@CallGroup { response: JmapResponse -> readThreads(response).withRows(response[rows].list) }
    }

private fun threadGroup(
    accountId: String,
    ids: List<String>,
): CallGroup<FetchedMail> = CallGroup(size = THREAD_CALLS) { threadCalls(accountId, JmapIds.Of(ids)) }

private fun memberGroup(
    accountId: String,
    ids: List<String>,
): CallGroup<FetchedMail> =
    CallGroup(size = 1) {
        val members = call(GetMethod(EmailType, accountId, JmapIds.Of(ids), MEMBER_PROPERTIES))
        return@CallGroup { response: JmapResponse ->
            FetchedMail(members = refusable { response[members].list }.orEmpty())
        }
    }

/** `Thread/get` of [threads], and an `Email/get` of their emails by back-reference. */
private fun JmapRequestBuilder.threadCalls(
    accountId: String,
    threads: JmapIds,
): (JmapResponse) -> FetchedMail {
    val found = call(GetMethod(ThreadType, accountId, threads))
    val members =
        call(GetMethod(EmailType, accountId, JmapIds.Result(found.resultOf("/list/*/emailIds")), MEMBER_PROPERTIES))
    return { response ->
        // A thread without its members is no use, so a refused member call drops both.
        refusable { FetchedMail(threads = response[found].list, members = response[members].list) } ?: FetchedMail()
    }
}

private fun FetchedMail.withRows(rows: List<EmailObject>) = FetchedMail(rows, threads, members)

private inline fun <T> refusable(read: () -> T): T? =
    try {
        read()
    } catch (expected: JmapException.MethodError) {
        null
    }

private val EmailObject.receivedMillis: Long?
    get() = Instant.parseOrNull(receivedAt)?.toEpochMilliseconds()

/** What a conversation row reads from each email of its thread. */
internal val MEMBER_PROPERTIES =
    listOf("id", "threadId", "from", "to", "cc", "receivedAt", "keywords", "mailboxIds", "hasAttachment")

private const val ROW_CALLS = 3
private const val THREAD_CALLS = 2

/** RFC 8620, section 2, suggests servers take at least this many ids per `/get`. */
private const val SUGGESTED_MAX_OBJECTS = 500
