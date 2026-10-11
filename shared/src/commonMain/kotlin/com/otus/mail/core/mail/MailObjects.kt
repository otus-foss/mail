package com.otus.mail.core.mail

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapType
import kotlinx.serialization.Serializable

/** Mailbox objects (RFC 8621, section 2). */
val MailboxType: JmapType<MailboxObject> = JmapType("Mailbox", JmapCapability.MAIL, MailboxObject.serializer())

/** Email objects (RFC 8621, section 4). */
val EmailType: JmapType<EmailObject> = JmapType("Email", JmapCapability.MAIL, EmailObject.serializer())

/** Email properties the inbox requests (RFC 8621, section 4.1). */
val EMAIL_PROPERTIES =
    listOf(
        "id",
        "mailboxIds",
        "keywords",
        "from",
        "subject",
        "receivedAt",
        "preview",
        "hasAttachment",
        "attachments",
        "textBody",
        "bodyValues",
    )

/** Mailbox properties folders request (RFC 8621, section 2). */
val MAILBOX_PROPERTIES =
    listOf("id", "name", "parentId", "role", "sortOrder", "totalEmails", "unreadEmails", "myRights")

@Serializable
data class MailboxObject(
    val id: String,
    val name: String = "",
    val parentId: String? = null,
    val role: String? = null,
    val sortOrder: Long = 0,
    val totalEmails: Long = 0,
    val unreadEmails: Long = 0,
    val myRights: MailboxRights = MailboxRights(),
)

/** What the user may do with a mailbox (RFC 8621, section 2). Missing rights read as allowed; the server decides. */
@Serializable
data class MailboxRights(
    val mayAddItems: Boolean = true,
    val mayRemoveItems: Boolean = true,
    val maySetSeen: Boolean = true,
    val maySetKeywords: Boolean = true,
    val mayCreateChild: Boolean = true,
    val mayRename: Boolean = true,
    val mayDelete: Boolean = true,
)

/** The mail account capability's limits (RFC 8621, section 1.3.1). */
@Serializable
data class MailCapability(
    val mayCreateTopLevelMailbox: Boolean = true,
    val maxMailboxDepth: Long? = null,
    val maxSizeMailboxName: Long? = null,
    val maxMailboxesPerEmail: Long? = null,
)

@Serializable
data class EmailObject(
    val id: String,
    val mailboxIds: Map<String, Boolean> = emptyMap(),
    val keywords: Map<String, Boolean> = emptyMap(),
    val from: List<EmailAddress>? = null,
    val subject: String? = null,
    val receivedAt: String,
    val preview: String = "",
    val hasAttachment: Boolean = false,
    val attachments: List<EmailBodyPart>? = null,
    val textBody: List<EmailBodyPart>? = null,
    val bodyValues: Map<String, EmailBodyValue> = emptyMap(),
)

@Serializable
data class EmailAddress(
    val name: String? = null,
    val email: String = "",
)

@Serializable
data class EmailBodyPart(
    val partId: String? = null,
    val name: String? = null,
)

@Serializable
data class EmailBodyValue(
    val value: String = "",
)
