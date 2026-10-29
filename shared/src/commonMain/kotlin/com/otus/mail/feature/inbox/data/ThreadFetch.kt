package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapIds
import com.otus.jmap.JmapRequestBuilder
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailThreadType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.ThreadObject
import com.otus.mail.core.mail.ThreadType

/**
 * Every Email in the thread of [emailId] with [properties], in one request of three chained calls:
 * `Email/get` for its `threadId`, `Thread/get` for the thread's Emails, and `Email/get` of those
 * (RFC 8621, sections 3 and 4.2). The server lists them oldest first.
 */
internal suspend fun MailAccount.threadEmails(
    emailId: String,
    properties: List<String>,
): List<EmailObject> =
    connection
        .request {
            val thread = threadOf(id, emailId)
            call(GetMethod(EmailType, id, JmapIds.Result(thread.resultOf("/list/*/emailIds")), properties))
        }.list

/**
 * The Email ids of the thread of [emailId], oldest first, from `Email/get` of its `threadId` and
 * `Thread/get` chained in one request. Empty when the server has no such email.
 */
internal suspend fun MailAccount.threadEmailIds(emailId: String): List<String> {
    val threads = connection.request { threadOf(id, emailId) }
    return threads.list.flatMap { it.emailIds }
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
