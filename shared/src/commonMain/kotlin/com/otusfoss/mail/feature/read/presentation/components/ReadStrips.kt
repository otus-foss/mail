package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentActions
import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentFile
import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentKind
import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentStrip
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_archive
import com.otusfoss.core.designsystem.resources.symbol_check
import com.otusfoss.core.designsystem.resources.symbol_content_copy
import com.otusfoss.core.designsystem.resources.symbol_delete
import com.otusfoss.core.designsystem.resources.symbol_key
import com.otusfoss.core.designsystem.resources.symbol_newspaper
import com.otusfoss.core.designsystem.resources.symbol_unsubscribe
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.feedback.components.InfoStrip
import com.otusfoss.core.designsystem.shared.rows.components.Unfold
import com.otusfoss.mail.core.codes.domain.SignInCode
import com.otusfoss.mail.core.messages.domain.MailAttachment
import com.otusfoss.mail.core.messages.presentation.AttachmentKindName
import com.otusfoss.mail.core.messages.presentation.attachmentSummary
import com.otusfoss.mail.core.messages.presentation.detail
import com.otusfoss.mail.core.messages.presentation.kind
import com.otusfoss.mail.core.messages.presentation.typeLabel
import com.otusfoss.mail.feature.read.presentation.NewsletterStep

/**
 * A message's [files] in the attachment strip, unfolded in place as far as [unfold] is, each with
 * its [thumbnail] once it has one. A file [kept] offline says so after its size.
 */
@Composable
internal fun ReadAttachmentStrip(
    files: List<MailAttachment>,
    unfold: Unfold,
    onToggle: () -> Unit,
    actions: AttachmentActions,
    thumbnail: (MailAttachment) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    kept: (MailAttachment) -> Boolean = { false },
) {
    AttachmentStrip(
        files.map { it.shown(kept(it), thumbnail(it)) },
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
                UiAction("Unsubscribe", Res.drawable.symbol_unsubscribe, onClick = onUnsubscribe),
                modifier,
            )
        }

        NewsletterStep.Unsubscribed -> {
            val waiting = if (older == 1) "1 older digest in your mail" else "$older older digests in your mail"
            val detail = listOfNotNull(TAKES, waiting.takeIf { older > 0 }).joinToString(" · ")
            val archive = UiAction("Archive all", Res.drawable.symbol_archive, onClick = onArchive).takeIf { older > 0 }
            InfoStrip(icon, UNSUBSCRIBED, detail, archive, modifier)
        }

        NewsletterStep.Archived -> {
            InfoStrip(icon, UNSUBSCRIBED, "$TAKES · older digests archived", null, modifier)
        }
    }
}

/** This file as the design system shows it, with its [thumbnail] and whether it is [kept] offline. */
internal fun MailAttachment.shown(
    kept: Boolean,
    thumbnail: ImageBitmap?,
): AttachmentFile = AttachmentFile(name, detail(kept), kind().shown, thumbnail, typeLabel())

/**
 * A one-time [code]'s strip: the code with Copy, then, once [copied], the code under "Copied" with
 * Delete, since the message has done its job. [onDelete] is null where the message cannot go.
 */
@Composable
internal fun ReadCodeStrip(
    code: SignInCode,
    copied: Boolean,
    onCopy: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (copied) {
        val delete = onDelete?.let { UiAction("Delete", Res.drawable.symbol_delete, onClick = it) }
        InfoStrip(Res.drawable.symbol_check, "Copied", code.shown, delete, modifier)
    } else {
        val copy = UiAction("Copy", Res.drawable.symbol_content_copy, onClick = onCopy)
        InfoStrip(Res.drawable.symbol_key, code.shown, ONE_TIME_CODE, copy, modifier)
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

/** What a code is called on its strip and on the clipboard. */
internal const val ONE_TIME_CODE = "One-time code"
private const val TAKES = "Takes up to 2 days"
