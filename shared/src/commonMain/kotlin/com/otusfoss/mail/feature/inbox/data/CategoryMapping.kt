package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.mail.core.categories.domain.CategoryReason
import com.otusfoss.mail.core.categories.domain.CategoryVerdict
import com.otusfoss.mail.core.categories.domain.MailCategory
import com.otusfoss.mail.core.categories.domain.ReasonKind
import com.otusfoss.mail.core.mail.EmailBodyPart
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.messages.data.isHtml
import com.otusfoss.mail.feature.inbox.domain.category.BulkHeaders
import com.otusfoss.mail.feature.inbox.domain.category.CategorySignals
import com.otusfoss.mail.feature.inbox.domain.category.schemaTypes
import kotlinx.serialization.Serializable

/**
 * What the classifier reads of this listed email: its sender, whether DMARC [verified] it, its
 * subject and headers, and from [body], the opened message's copy when the device has one, its
 * text and schema.org markup. Without a body the server's preview stands in for the text.
 */
internal fun EmailObject.categorySignals(
    body: EmailObject?,
    verified: Boolean,
): CategorySignals {
    val html = body?.partValues(body.htmlBody.orEmpty().filter { it.isHtml }).orEmpty()
    val text = body?.partValues(body.textBody.orEmpty().filterNot { it.isHtml })?.joinToString("\n")
    return CategorySignals(
        senderAddress =
            from
                ?.firstOrNull()
                ?.email
                .orEmpty()
                .trim(),
        senderVerified = verified,
        subject = subject.orEmpty(),
        text = text?.ifBlank { null } ?: preview,
        headers = bulkHeaders,
        markup = html.flatMap(::schemaTypes).toSet(),
    )
}

private val EmailObject.bulkHeaders: BulkHeaders
    get() =
        BulkHeaders(
            listId = listId?.trim()?.ifEmpty { null },
            listUnsubscribe = !listUnsubscribe.isNullOrEmpty(),
            listPost = listPost?.trim()?.ifEmpty { null },
            precedence = precedence?.trim()?.lowercase(),
            autoSubmitted = autoSubmitted,
            feedbackId = feedbackId?.trim()?.ifEmpty { null },
            platforms =
                listOfNotNull(
                    "Mailchimp".takeIf { mailchimp != null },
                    "SendGrid".takeIf { sendGrid != null },
                    "Amazon SES".takeIf { amazonSes != null },
                    "Mailgun".takeIf { mailgun != null },
                    "a certified bulk sender".takeIf { certifiedSender != null },
                ),
            forge =
                listOfNotNull(
                    "X-GitHub-Reason".takeIf { githubReason != null },
                    "X-GitLab-Project".takeIf { gitlabProject != null },
                    "X-Gitea-Reason".takeIf { giteaReason != null },
                    "X-Forgejo-Reason".takeIf { forgejoReason != null },
                ).firstOrNull(),
        )

private fun EmailObject.partValues(parts: List<EmailBodyPart>): List<String> =
    parts.mapNotNull { part -> part.partId?.let { bodyValues[it]?.value } }

/** A [CategoryVerdict] as the cache keeps it. Names, not ordinals, so reordering the enums keeps it. */
@Serializable
internal data class StoredCategory(
    val version: String,
    val category: String,
    val reasons: List<StoredReason> = emptyList(),
    val timeSensitive: Boolean = false,
) {
    /** The verdict when the [current] classifier made it and its names are known; else null, to classify again. */
    fun verdict(current: String): CategoryVerdict? {
        val found = MailCategory.fromStorageValue(category)
        val known = reasons.mapNotNull { it.reason() }
        if (version != current || found == null || known.size != reasons.size) return null
        return CategoryVerdict(found, known, timeSensitive, version)
    }

    companion object {
        fun of(verdict: CategoryVerdict): StoredCategory =
            StoredCategory(
                verdict.version,
                verdict.category.storageValue,
                verdict.reasons.map { StoredReason(it.kind.storageValue, it.detail) },
                verdict.timeSensitive,
            )
    }
}

@Serializable
internal data class StoredReason(
    val kind: String,
    val detail: String = "",
) {
    fun reason(): CategoryReason? = ReasonKind.fromStorageValue(kind)?.let { CategoryReason(it, detail) }
}
