package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.otus.designsystem.mail.attachments.components.AttachmentActions
import com.otus.designsystem.mail.attachments.components.AttachmentFile
import com.otus.designsystem.mail.attachments.components.AttachmentKind
import com.otus.designsystem.mail.attachments.components.AttachmentStrip
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_archive
import com.otus.designsystem.resources.symbol_newspaper
import com.otus.designsystem.resources.symbol_unsubscribe
import com.otus.designsystem.shared.feedback.components.InfoStrip
import com.otus.designsystem.shared.feedback.components.StripAction
import com.otus.designsystem.shared.rows.components.Unfold
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.presentation.NewsletterStep
import com.otus.mail.feature.inbox.presentation.read.AttachmentKindName
import com.otus.mail.feature.inbox.presentation.read.attachmentSummary
import com.otus.mail.feature.inbox.presentation.read.detail
import com.otus.mail.feature.inbox.presentation.read.kind

/**
 * A message's [files] in the attachment strip, unfolded in place as far as [unfold] is, each with
 * its [thumbnail] once it has one.
 */
@Composable
internal fun ReadAttachmentStrip(
    files: List<MailAttachment>,
    unfold: Unfold,
    onToggle: () -> Unit,
    actions: AttachmentActions,
    thumbnail: (MailAttachment) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    AttachmentStrip(
        files.map { AttachmentFile(it.name, it.detail(), it.kind().shown, thumbnail(it)) },
        attachmentSummary(files),
        unfold,
        onToggle,
        actions,
        modifier,
    )
}

/**
 * What a newsletter's strip says at each [step]: its [title] and [subtitle] with Unsubscribe, then
 * that it is done with Archive all while [older] mail of the list waits in the account.
 */
@Composable
internal fun ReadNewsletterStrip(
    title: String,
    subtitle: String,
    step: NewsletterStep,
    older: Int,
    onUnsubscribe: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = Res.drawable.symbol_newspaper
    when (step) {
        NewsletterStep.Subscribed -> {
            InfoStrip(
                icon,
                title,
                subtitle,
                StripAction("Unsubscribe", Res.drawable.symbol_unsubscribe, onUnsubscribe),
                modifier,
            )
        }

        NewsletterStep.Unsubscribed -> {
            val waiting = if (older == 1) "1 older digest in your mail" else "$older older digests in your mail"
            val detail = listOfNotNull(TAKES, waiting.takeIf { older > 0 }).joinToString(" · ")
            val archive = StripAction("Archive all", Res.drawable.symbol_archive, onArchive).takeIf { older > 0 }
            InfoStrip(icon, UNSUBSCRIBED, detail, archive, modifier)
        }

        NewsletterStep.Archived -> {
            InfoStrip(icon, UNSUBSCRIBED, "$TAKES · older digests archived", null, modifier)
        }
    }
}

private val AttachmentKindName.shown: AttachmentKind
    get() =
        when (this) {
            AttachmentKindName.Image -> AttachmentKind.Image
            AttachmentKindName.Pdf -> AttachmentKind.Pdf
            AttachmentKindName.Document -> AttachmentKind.Document
            AttachmentKindName.Sheet -> AttachmentKind.Sheet
            AttachmentKindName.Slides -> AttachmentKind.Slides
            AttachmentKindName.Archive -> AttachmentKind.Archive
            AttachmentKindName.Audio -> AttachmentKind.Audio
            AttachmentKindName.Video -> AttachmentKind.Video
            AttachmentKindName.Calendar -> AttachmentKind.Calendar
            AttachmentKindName.Contact -> AttachmentKind.Contact
            AttachmentKindName.Code -> AttachmentKind.Code
            AttachmentKindName.Other -> AttachmentKind.Other
        }

private const val UNSUBSCRIBED = "Unsubscribed"
private const val TAKES = "Takes up to 2 days"
