package com.otus.mail.feature.inbox.data

import com.otus.mail.feature.inbox.domain.MailConversation
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.ThreadEmail
import com.otus.mail.feature.inbox.domain.ThreadMember

/**
 * A server's answer to a list request: the `Email/query` [ids], the row emails in [list], and the
 * thread emails in [members]. The `Thread/get` result stays empty, since the list reads only the
 * thread emails it returns.
 */
internal fun emailListResponse(
    ids: String,
    list: String,
    members: String = "",
): String =
    """{"methodResponses": [["Email/query", {"ids": [$ids]}, "c0"],
        ["Email/get", {"state": "e", "list": [$list]}, "c1"],
        ["Thread/get", {"state": "t", "list": []}, "c2"],
        ["Email/get", {"state": "e", "list": [$members]}, "c3"]]}"""

/** A conversation of [members], for actions that read only who belongs to it. */
internal fun conversationOf(members: List<ThreadMember>): MailConversation =
    MailConversation(
        threadId = "t1",
        messageCount = members.size,
        last = ThreadEmail(MailSender("Grace", "grace@example.org"), emptyList(), ReceivedAt("2026-10-04", "09:30")),
        senders = emptyList(),
        answered = false,
        members = members,
    )
