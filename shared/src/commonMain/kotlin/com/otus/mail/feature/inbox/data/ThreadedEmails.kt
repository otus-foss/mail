package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.mail.core.mail.EMAIL_PROPERTIES
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.NEWEST_FIRST
import com.otus.mail.core.mail.ThreadType
import com.otus.mail.core.mail.inMailbox
import kotlinx.serialization.json.JsonPrimitive

/** A mailbox's newest email of each thread, as [rows], and every email of those threads by thread id. */
internal class ThreadedEmails(
    val rows: List<EmailObject>,
    val threads: Map<String, List<EmailObject>>,
)

/**
 * The newest [limit] conversations of [mailboxId] in one request (RFC 8621, sections 3 and 4.4):
 * `Email/query` with `collapseThreads` keeps the newest email of each thread, `Email/get` reads
 * those rows, `Thread/get` finds their threads, and a second `Email/get` reads every thread email
 * with only what a row counts. When the server refuses the thread calls, for example because a
 * thread holds more emails than one `/get` may return, each row stands for its own email.
 */
internal suspend fun JmapConnection.threadedEmails(
    accountId: String,
    mailboxId: String,
    limit: Int,
): ThreadedEmails {
    lateinit var rows: JmapCall<GetResult<EmailObject>>
    lateinit var members: JmapCall<GetResult<EmailObject>>
    val response =
        send {
            val query = call(QueryMethod(EmailType, accountId, inMailbox(mailboxId), NEWEST_FIRST, limit, COLLAPSED))
            rows = call(GetMethod(EmailType, accountId, JmapIds.Result(query.resultOf("/ids")), EMAIL_PROPERTIES))
            val threads = call(GetMethod(ThreadType, accountId, JmapIds.Result(rows.resultOf("/list/*/threadId"))))
            members =
                call(
                    GetMethod(
                        EmailType,
                        accountId,
                        JmapIds.Result(threads.resultOf("/list/*/emailIds")),
                        MEMBER_PROPERTIES,
                    ),
                )
        }
    val threads =
        try {
            response[members].list.groupBy { it.threadId }
        } catch (expected: JmapException.MethodError) {
            emptyMap()
        }
    return ThreadedEmails(response[rows].list, threads)
}

private val COLLAPSED = mapOf("collapseThreads" to JsonPrimitive(true))

/** What a conversation row reads from each email of its thread. */
private val MEMBER_PROPERTIES =
    listOf("id", "threadId", "from", "to", "cc", "receivedAt", "keywords", "mailboxIds", "hasAttachment")
