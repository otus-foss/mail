package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.GetResult
import com.otusfoss.core.jmap.JmapCall
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.JmapRequestBuilder
import com.otusfoss.core.jmap.JmapResponse
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.EmailThreadType
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.ThreadObject
import com.otusfoss.mail.core.mail.ThreadType
import com.otusfoss.mail.core.mail.unlessRefused

/**
 * Every Email in the thread of [emailId] with [properties]. One request chains `Email/get` for its
 * `threadId`, `Thread/get` for the thread's Emails, and `Email/get` of those (RFC 8621, sections 3
 * and 4.2). When the server refuses the last call, for example because the thread holds more Emails
 * than one `/get` may return (`requestTooLarge`, RFC 8620, section 5.1), they follow in calls of at
 * most `maxObjectsInGet` ids. The server lists them oldest first.
 */
internal suspend fun MailAccount.threadEmails(
    emailId: String,
    properties: List<String>,
): List<EmailObject> {
    lateinit var thread: JmapCall<GetResult<ThreadObject>>
    lateinit var emails: JmapCall<GetResult<EmailObject>>
    val response =
        connection.send {
            thread = threadOf(id, emailId)
            emails = call(GetMethod(EmailType, id, JmapIds.Result(thread.resultOf("/list/*/emailIds")), properties))
        }
    return unlessRefused { response[emails].list }
        ?: connection
            .sendAll(
                response[thread]
                    .list
                    .flatMap { it.emailIds }
                    .chunked(session.getBatch)
                    .map { ids -> emailGroup(ids, properties) },
            ).flatten()
}

/**
 * The Email ids of the thread of [emailId], oldest first, from `Email/get` of its `threadId` and
 * `Thread/get` chained in one request. Empty when the server has no such email.
 */
internal suspend fun MailAccount.threadEmailIds(emailId: String): List<String> =
    connection
        .request { threadOf(id, emailId) }
        .list
        .flatMap { it.emailIds }

/** `Email/get` of [ids] with [properties], to send among others. */
private fun MailAccount.emailGroup(
    ids: List<String>,
    properties: List<String>,
): CallGroup<List<EmailObject>> =
    CallGroup(size = 1) {
        val emails = call(GetMethod(EmailType, id, JmapIds.Of(ids), properties))
        return@CallGroup { response: JmapResponse -> response[emails].list }
    }

/** `Email/get` of [emailId]'s `threadId`, and `Thread/get` of that thread by back-reference. */
private fun JmapRequestBuilder.threadOf(
    accountId: String,
    emailId: String,
): JmapCall<GetResult<ThreadObject>> {
    val original = call(GetMethod(EmailThreadType, accountId, JmapIds.Of(listOf(emailId)), THREAD_ID_PROPERTIES))
    return call(GetMethod(ThreadType, accountId, JmapIds.Result(original.resultOf("/list/*/threadId"))))
}

private val THREAD_ID_PROPERTIES = listOf("id", "threadId")
