package com.otus.mail.core.mail

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapType
import kotlinx.serialization.Serializable

/** Mailbox objects (RFC 8621, section 2). */
val MailboxType: JmapType<MailboxObject> = JmapType("Mailbox", JmapCapability.MAIL, MailboxObject.serializer())

/** Email objects (RFC 8621, section 4). */
val EmailType: JmapType<EmailObject> = JmapType("Email", JmapCapability.MAIL, EmailObject.serializer())

/** Only the mailboxes of each Email, for callers that need nothing else. */
val EmailFoldersType: JmapType<EmailFolders> = JmapType("Email", JmapCapability.MAIL, EmailFolders.serializer())

/** Identities an account may send as (RFC 8621, section 6). */
val IdentityType: JmapType<IdentityObject> =
    JmapType("Identity", JmapCapability.SUBMISSION, IdentityObject.serializer())

/** Email properties the inbox requests (RFC 8621, section 4.1). Recipients and ids let a reply quote and thread. */
val EMAIL_PROPERTIES =
    listOf(
        "id",
        "mailboxIds",
        "keywords",
        "from",
        "to",
        "cc",
        "messageId",
        "references",
        "subject",
        "receivedAt",
        "preview",
        "hasAttachment",
        "attachments",
        "textBody",
        "bodyValues",
    )

/** The Email properties [EmailFolders] reads. */
val EMAIL_FOLDERS_PROPERTIES = listOf("id", "mailboxIds")

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
    val to: List<EmailAddress>? = null,
    val cc: List<EmailAddress>? = null,
    val messageId: List<String>? = null,
    val references: List<String>? = null,
    val subject: String? = null,
    val receivedAt: String,
    val preview: String = "",
    val hasAttachment: Boolean = false,
    val attachments: List<EmailBodyPart>? = null,
    val textBody: List<EmailBodyPart>? = null,
    val bodyValues: Map<String, EmailBodyValue> = emptyMap(),
)

/** The mailboxes an Email is in, without its other properties. */
@Serializable
data class EmailFolders(
    val id: String,
    val mailboxIds: Map<String, Boolean> = emptyMap(),
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

@Serializable
data class IdentityObject(
    val id: String,
    val name: String = "",
    val email: String = "",
)
