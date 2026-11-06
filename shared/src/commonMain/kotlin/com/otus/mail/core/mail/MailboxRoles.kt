package com.otus.mail.core.mail

import com.otus.jmap.QueryMethod
import com.otus.jmap.filterCondition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put

// The mailbox roles Mail gives a place of their own (RFC 8621, section 2).
const val INBOX_ROLE = "inbox"
const val DRAFTS_ROLE = "drafts"
const val SENT_ROLE = "sent"
const val ARCHIVE_ROLE = "archive"
const val JUNK_ROLE = "junk"
const val TRASH_ROLE = "trash"

/** Trash and Spam, where discarded mail goes. */
internal val BIN_ROLES = setOf(TRASH_ROLE, JUNK_ROLE)

/** The `Mailbox/query` for the mailbox with [role], such as [DRAFTS_ROLE] or [SENT_ROLE]. */
internal fun MailAccount.roleQuery(role: String): QueryMethod =
    QueryMethod(MailboxType, id, filterCondition("role", JsonPrimitive(role)))
