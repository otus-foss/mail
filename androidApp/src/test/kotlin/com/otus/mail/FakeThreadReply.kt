package com.otus.mail

/**
 * A fake server's answer to the mail list's request for the single email [email] with id m1 in
 * thread T1: the query, the row email, its thread, and the thread's emails.
 */
internal fun oneThreadReply(email: String): String {
    val list = """{"state": "e", "list": [$email]}"""
    return """["Email/query", {"ids": ["m1"]}, "c0"], ["Email/get", $list, "c1"],
        ["Thread/get", {"state": "t", "list": [{"id": "T1", "emailIds": ["m1"]}]}, "c2"],
        ["Email/get", $list, "c3"]"""
}

/** Email m1 of thread T1 from Grace Hopper, in the mailboxes [mailboxIds] lists as JSON members. */
internal fun graceEmail(
    subject: String,
    mailboxIds: String,
): String =
    """{"id": "m1", "threadId": "T1", "mailboxIds": {$mailboxIds},
       "from": [{"name": "Grace Hopper", "email": "grace@example.org"}],
       "subject": "$subject", "receivedAt": "2026-10-04T09:30:00Z", "preview": "Hello"}"""
