package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.JmapConnection
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.JmapRequestBuilder
import com.otusfoss.core.jmap.JmapResponse
import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.CacheWrite
import com.otusfoss.mail.core.mail.EMAIL_PROPERTIES
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.LIST_ID_PROPERTY
import com.otusfoss.mail.core.mail.LIST_UNSUBSCRIBE_PROPERTY
import com.otusfoss.mail.core.mail.ThreadObject
import com.otusfoss.mail.core.mail.ThreadType
import com.otusfoss.mail.core.mail.putObject
import com.otusfoss.mail.core.mail.received
import com.otusfoss.mail.core.mail.unlessRefused

/**
 * What a list still needs from the server: [rows] to read with every row property, [threads] of
 * rows the device already holds, and thread [members] to read with only what a row counts.
 */
internal class WantedMail(
    val rows: Set<String>,
    val threads: Set<String> = emptySet(),
    val members: Set<String> = emptySet(),
)

/**
 * Row emails, threads, and thread member emails the server sent, and the emails of [threads] it
 * would not send in one call, which [unreadMembers] lists.
 */
internal class FetchedMail(
    val rows: List<EmailObject> = emptyList(),
    val threads: List<ThreadObject> = emptyList(),
    val members: List<EmailObject> = emptyList(),
    val unreadMembers: List<String> = emptyList(),
)

/**
 * Reads [wanted] in as few requests as the server allows (RFC 8621, sections 3 and 4.2). Each
 * `Email/get` of rows chains `Thread/get` of their threads and an `Email/get` of those threads'
 * emails by back-reference, so new rows arrive with their conversations. When the server refuses
 * the back-referenced emails, for example because the threads hold more emails than one `/get` may
 * return (`requestTooLarge`, RFC 8620, section 5.1), they follow in calls of at most
 * `maxObjectsInGet` ids. When it refuses a thread call, those rows stand for their own emails.
 */
internal suspend fun JmapConnection.fetchMail(
    accountId: String,
    wanted: WantedMail,
): FetchedMail {
    val chunk = session().getBatch
    val groups =
        wanted.rows.chunked(chunk).map { rowGroup(accountId, it) } +
            wanted.threads.chunked(chunk).map { threadGroup(accountId, it) } +
            wanted.members.chunked(chunk).map { memberGroup(accountId, it) }
    val parts = sendAll(groups)
    val unread = parts.flatMap { it.unreadMembers }.distinct()
    val later = sendAll(unread.chunked(chunk).map { memberGroup(accountId, it) })
    return FetchedMail(
        rows = parts.flatMap { it.rows },
        threads = parts.flatMap { it.threads },
        members = (parts + later).flatMap { it.members },
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
            FetchedMail(members = unlessRefused { response[members].list }.orEmpty())
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
        val threads = unlessRefused { response[found].list }.orEmpty()
        val read = unlessRefused { response[members].list }
        val unread = if (read == null) threads.flatMap { it.emailIds } else emptyList()
        FetchedMail(threads = threads, members = read.orEmpty(), unreadMembers = unread)
    }
}

private fun FetchedMail.withRows(rows: List<EmailObject>) = FetchedMail(rows, threads, members, unreadMembers)

/** When the email arrived, in epoch milliseconds, for the cache to trim old mail by. */
internal val EmailObject.receivedMillis: Long?
    get() = received?.toEpochMilliseconds()

/** What a conversation row reads from each email of its thread. */
internal val MEMBER_PROPERTIES =
    listOf(
        "id",
        "threadId",
        "from",
        "to",
        "cc",
        "receivedAt",
        "keywords",
        "mailboxIds",
        "hasAttachment",
        "attachments",
        LIST_ID_PROPERTY,
        LIST_UNSUBSCRIBE_PROPERTY,
    )

private const val ROW_CALLS = 3
private const val THREAD_CALLS = 2
