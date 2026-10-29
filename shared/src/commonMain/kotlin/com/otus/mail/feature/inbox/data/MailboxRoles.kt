package com.otus.mail.feature.inbox.data

import com.otus.jmap.QueryMethod
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The mailbox with [role], such as [DRAFTS_ROLE] or [SENT_ROLE] (RFC 8621, section 2). */
internal fun MailAccount.roleQuery(role: String): QueryMethod =
    QueryMethod(MailboxType, id, buildJsonObject { put("role", role) })

internal const val DRAFTS_ROLE = "drafts"
internal const val SENT_ROLE = "sent"
internal const val ARCHIVE_ROLE = "archive"

/** Trash and Spam: their mail counts only where the list shows that folder. */
internal val BIN_ROLES = setOf("trash", "junk")
