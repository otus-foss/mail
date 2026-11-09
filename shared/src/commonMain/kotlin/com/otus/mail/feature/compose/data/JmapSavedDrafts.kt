package com.otus.mail.feature.compose.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapIds
import com.otus.jmap.jmapCallOr
import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.messages.data.isHtml
import com.otus.mail.core.messages.data.toSender
import com.otus.mail.core.messages.domain.AttachmentBlob
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.DraftAttachment
import com.otus.mail.feature.compose.domain.SavedDrafts
import com.otus.mail.feature.compose.domain.SavedText
import com.otus.mail.feature.compose.domain.savedHtmlBody
import com.otus.mail.feature.compose.domain.savedParts
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Loads a saved draft from its Email (RFC 8621, section 4.2), with every body value whole, so a
 * save from Write never stores less than the copy had: the formatting, the full text, and each
 * file, inline images still inline.
 */
internal class JmapSavedDrafts(
    private val connections: JmapConnections,
) : SavedDrafts {
    override suspend fun load(
        accountId: String,
        emailId: String,
        whole: Boolean,
    ): Draft? =
        jmapCallOr(null) {
            connections
                .mailAccount(accountId)
                ?.let { account ->
                    account.connection
                        .call(GetMethod(EmailType, account.id, JmapIds.Of(listOf(emailId)), PROPERTIES, ARGUMENTS))
                        .list
                        .firstOrNull()
                }?.toDraft(accountId, whole)
        }
}

/** The draft this saved Email holds, [whole] or split for Write, which adds the identity's signature. */
private fun EmailObject.toDraft(
    accountId: String,
    whole: Boolean,
): Draft {
    val text = textBody.orEmpty().joinToString("\n") { bodyValues[it.partId]?.value.orEmpty() }
    val parts = if (whole) SavedText(text) else text.savedParts()
    val files = (htmlBody.orEmpty() + attachments.orEmpty()).filter { it.blobId != null && !it.isBody }
    val html =
        htmlBody
            .orEmpty()
            .firstOrNull { it.isHtml }
            ?.let { bodyValues[it.partId]?.value }
            ?.savedHtmlBody(endsEarly = parts.body != text.trimEnd())
            .orEmpty()
    val inline = files.filter { file -> file.cid != null && "cid:${file.cid}" in html }
    return Draft(
        accountId = accountId,
        from = from?.firstOrNull()?.toSender(accountId),
        to = to.orEmpty().map { it.toSender(accountId) },
        cc = cc.orEmpty().map { it.toSender(accountId) },
        bcc = bcc.orEmpty().map { it.toSender(accountId) },
        subject = subject.orEmpty(),
        body = parts.body,
        html = inline.fold(html) { body, file -> body.replace("cid:${file.cid}", "cid:${file.draftId}@otus.mail") },
        quote = parts.quote,
        references = references.orEmpty(),
        inReplyTo = references.orEmpty().takeLast(1),
        emailId = id,
        attachments = files.distinctBy { it.blobId }.map { it.draftFile(accountId, inline = it in inline) },
    )
}

/** A part of the body itself, its text or HTML, rather than a file. */
private val EmailBodyPart.isBody: Boolean
    get() = type.equals("text/plain", ignoreCase = true) || isHtml

/** The file's draft id, which its `cid` is made from; Write's own inline images keep theirs. */
private val EmailBodyPart.draftId: String
    get() = cid?.removeSuffix("@otus.mail")?.takeIf { it != cid } ?: blobId.orEmpty()

private fun EmailBodyPart.draftFile(
    accountId: String,
    inline: Boolean,
): DraftAttachment =
    DraftAttachment(
        id = draftId,
        name = name.orEmpty(),
        type = type.orEmpty(),
        size = size,
        blob = AttachmentBlob(accountId, blobId.orEmpty()),
        inline = inline,
    )

private val PROPERTIES =
    listOf(
        "id",
        "from",
        "to",
        "cc",
        "bcc",
        "subject",
        "references",
        "receivedAt",
        "textBody",
        "htmlBody",
        "attachments",
        "bodyValues",
    )

/** Every body value whole: no `maxBodyValueBytes`, so none comes back truncated. */
private val ARGUMENTS =
    mapOf(
        "bodyProperties" to
            JsonArray(listOf("partId", "type", "blobId", "size", "name", "cid", "disposition").map(::JsonPrimitive)),
        "fetchAllBodyValues" to JsonPrimitive(true),
    )
