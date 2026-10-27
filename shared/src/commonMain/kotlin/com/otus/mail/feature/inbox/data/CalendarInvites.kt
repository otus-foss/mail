package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailObject

/**
 * A calendar invitation: a `text/calendar` part, or a file of the iCalendar type or extensions
 * (RFC 5545, section 8.1). An iTIP part's `method=` sits in its Content-Type parameters, which
 * JMAP's `type` leaves out (RFC 8621, section 4.1.4), so the type alone marks it. Some senders label
 * the file `application/ics` or only name it, which the other checks catch.
 */
internal fun EmailBodyPart.isInvite(): Boolean {
    val mediaType =
        type
            .orEmpty()
            .substringBefore(';')
            .trim()
            .lowercase()
    val extension = name.orEmpty().substringAfterLast('.', "").lowercase()
    return mediaType in INVITE_TYPES || extension in INVITE_EXTENSIONS
}

/** Whether any attachment of this Email is a calendar invitation. */
internal val EmailObject.hasInvite: Boolean
    get() = attachments.orEmpty().any { it.isInvite() }

/**
 * The server's `hasAttachment`, unless every attachment is a calendar invitation, which the row
 * marks with a calendar instead of the paperclip. Without an `attachments` list the server's answer
 * stands.
 */
internal val EmailObject.hasFile: Boolean
    get() {
        val parts = attachments.orEmpty()
        return hasAttachment && (parts.isEmpty() || parts.any { !it.isInvite() })
    }

private val INVITE_TYPES = setOf("text/calendar", "application/ics")
private val INVITE_EXTENSIONS = setOf("ics", "ifb")
