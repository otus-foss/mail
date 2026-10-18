package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapIds
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailThreadType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
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
            val original = call(GetMethod(EmailThreadType, id, JmapIds.Of(listOf(emailId)), THREAD_ID_PROPERTIES))
            val thread = call(GetMethod(ThreadType, id, JmapIds.Result(original.resultOf("/list/*/threadId"))))
            call(GetMethod(EmailType, id, JmapIds.Result(thread.resultOf("/list/*/emailIds")), properties))
        }.list

private val THREAD_ID_PROPERTIES = listOf("id", "threadId")
