package com.otus.mail.core.mail

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Mailbox objects (RFC 8621, section 2). */
val MailboxType: JmapType<MailboxObject> = JmapType("Mailbox", JmapCapability.MAIL, MailboxObject.serializer())

/** Email objects (RFC 8621, section 4). */
val EmailType: JmapType<EmailObject> = JmapType("Email", JmapCapability.MAIL, EmailObject.serializer())

/** Only the mailboxes of each Email, for callers that need nothing else. */
val EmailFoldersType: JmapType<EmailFolders> = JmapType("Email", JmapCapability.MAIL, EmailFolders.serializer())

/** Identities an account may send as (RFC 8621, section 6). */
val IdentityType: JmapType<IdentityObject> =
    JmapType("Identity", JmapCapability.SUBMISSION, IdentityObject.serializer())

/** Submissions that send an Email (RFC 8621, section 7). Mail only creates them, so their objects stay JSON. */
val EmailSubmissionType: JmapType<JsonObject> =
    JmapType("EmailSubmission", JmapCapability.SUBMISSION, JsonObject.serializer())

/**
 * Email properties the inbox requests for each row (RFC 8621, section 4.1). Recipients and ids let
 * a reply quote and thread. The list shows `preview`; an open message loads its body separately.
 * The list headers tell newsletters apart.
 */
val EMAIL_PROPERTIES =
    listOf(
        "id",
        "threadId",
        "mailboxIds",
        "keywords",
        "from",
        "replyTo",
        "to",
        "cc",
        "messageId",
        "references",
        "subject",
        "receivedAt",
        "preview",
        "hasAttachment",
        "attachments",
        LIST_ID_PROPERTY,
        LIST_UNSUBSCRIBE_PROPERTY,
        AUTHENTICATION_RESULTS_PROPERTY,
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
    val threadId: String = "",
    val mailboxIds: Map<String, Boolean> = emptyMap(),
    val keywords: Map<String, Boolean> = emptyMap(),
    val from: List<EmailAddress>? = null,
    val replyTo: List<EmailAddress>? = null,
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
    /** The parts to show as HTML, which may include plain parts (RFC 8621, section 4.1.4). */
    val htmlBody: List<EmailBodyPart>? = null,
    val bodyValues: Map<String, EmailBodyValue> = emptyMap(),
    val bcc: List<EmailAddress>? = null,
    /** The raw message's size in octets. */
    val size: Long = 0,
    /** Every header field, raw and in message order, for View details. */
    val headers: List<EmailHeader>? = null,
    /** The `List-Unsubscribe` addresses (RFC 2369), parsed by the server (RFC 8621, section 4.1.2.6). */
    @SerialName(LIST_UNSUBSCRIBE_PROPERTY)
    val listUnsubscribe: List<String>? = null,
    /** `List-Unsubscribe=One-Click` when the list takes the RFC 8058 one-click POST. */
    @SerialName("header:List-Unsubscribe-Post:asText")
    val listUnsubscribePost: String? = null,
    /** The list's name and id, such as `Weekly digest <weekly.example.org>` (RFC 2919). */
    @SerialName(LIST_ID_PROPERTY)
    val listId: String? = null,
    /** Every `Delivered-To` header's addresses, which name the mailbox a Bcc'd list reached. */
    @SerialName(DELIVERED_TO_PROPERTY)
    val deliveredTo: List<List<EmailAddress>?>? = null,
    /** Every `X-Original-To` header's addresses, which some servers write in place of `Delivered-To`. */
    @SerialName(ORIGINAL_TO_PROPERTY)
    val originalTo: List<List<EmailAddress>?>? = null,
    /** Every `Authentication-Results` field (RFC 8601), topmost first, which tell a DMARC pass. */
    @SerialName(AUTHENTICATION_RESULTS_PROPERTY)
    val authenticationResults: List<String>? = null,
) {
    /** Whether a mailing list or newsletter sent it. */
    val fromList: Boolean
        get() = listId != null || !listUnsubscribe.isNullOrEmpty()

    /**
     * The addresses the user's server delivered it to, from the topmost `Delivered-To` and
     * `X-Original-To`. Only the topmost of each header comes from that server; a sender can write
     * more below it to learn which aliases belong together.
     */
    val deliveredAddresses: List<String>
        get() =
            (deliveredTo?.firstOrNull().orEmpty() + originalTo?.firstOrNull().orEmpty())
                .map { it.email.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
}

/** The `Email/get` properties that read a mailing list's headers, which mark newsletters. */
const val LIST_ID_PROPERTY = "header:List-Id:asText"
const val AUTHENTICATION_RESULTS_PROPERTY = "header:Authentication-Results:asText:all"
const val LIST_UNSUBSCRIBE_PROPERTY = "header:List-Unsubscribe:asURLs"

/** The `Email/get` properties that read the delivery headers behind [EmailObject.deliveredAddresses]. */
const val DELIVERED_TO_PROPERTY = "header:Delivered-To:asAddresses:all"
const val ORIGINAL_TO_PROPERTY = "header:X-Original-To:asAddresses:all"

/** One header field as the message has it (RFC 8621, section 4.1.3). */
@Serializable
data class EmailHeader(
    val name: String = "",
    val value: String = "",
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
    /** The media type, such as `text/html`, which a body may be when it has no plain alternative. */
    val type: String? = null,
    val blobId: String? = null,
    /** The decoded size in octets. */
    val size: Long = 0,
    /** The Content-Id without angle brackets, which HTML refers to as `cid:` (RFC 2392). */
    val cid: String? = null,
    /** `inline` or `attachment`, from the Content-Disposition header. */
    val disposition: String? = null,
)

/** A part's decoded text; [isTruncated] when it is longer than the request's `maxBodyValueBytes`. */
@Serializable
data class EmailBodyValue(
    val value: String = "",
    val isTruncated: Boolean = false,
)

@Serializable
data class IdentityObject(
    val id: String,
    val name: String = "",
    val email: String = "",
    val textSignature: String = "",
    val htmlSignature: String = "",
)

/**
 * Marks an email of a muted conversation. A conversation is muted while any of its emails has it,
 * so new mail in it does not notify. Not a registered keyword, so other apps ignore it.
 */
const val MUTED_KEYWORD = "\$muted"

/** Only the thread of each Email, to find the rest of a conversation (RFC 8621, section 3). */
val EmailThreadType: JmapType<EmailThread> = JmapType("Email", JmapCapability.MAIL, EmailThread.serializer())

@Serializable
data class EmailThread(
    val id: String,
    val threadId: String = "",
)

/** A conversation's Emails, oldest first (RFC 8621, section 3). */
val ThreadType: JmapType<ThreadObject> = JmapType("Thread", JmapCapability.MAIL, ThreadObject.serializer())

@Serializable
data class ThreadObject(
    val id: String,
    val emailIds: List<String> = emptyList(),
)
