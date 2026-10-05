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

@Serializable
data class MailboxObject(
    val id: String,
    val name: String = "",
    val role: String? = null,
)

@Serializable
data class EmailObject(
    val id: String,
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
