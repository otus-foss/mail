package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentFile
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
import com.otusfoss.mail.core.codes.domain.SignInCode
import com.otusfoss.mail.core.codes.presentation.components.ONE_TIME_CODE
import com.otusfoss.mail.core.messages.domain.MailAttachment
import com.otusfoss.mail.core.messages.presentation.detail
import com.otusfoss.mail.core.messages.presentation.kind
import com.otusfoss.mail.core.messages.presentation.symbol
import com.otusfoss.mail.core.messages.presentation.typeLabel
import com.otusfoss.mail.feature.read.presentation.NewsletterStep

/**
 * A newsletter's strip at each [step]: its [title] and [subtitle] with Unsubscribe. Once
 * unsubscribed, the card goes and only "Archive [older]" stays in its place while older mail of the
 * list waits in the account; after that, or with none waiting, the strip is gone.
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
    val subscribed = step == NewsletterStep.Subscribed
    if (step == NewsletterStep.Archived || (!subscribed && older == 0)) return
    val action =
        if (subscribed) {
            UiAction("Unsubscribe", Res.drawable.symbol_unsubscribe, onClick = onUnsubscribe)
        } else {
            UiAction("Archive $older", Res.drawable.symbol_archive, onClick = onArchive)
        }
    InfoStrip(Res.drawable.symbol_newspaper, title, subtitle, action, modifier, framed = subscribed)
}

/** This file as the design system shows it, with its [thumbnail] and whether it is [kept] offline. */
internal fun MailAttachment.shown(
    kept: Boolean,
    thumbnail: ImageBitmap?,
): AttachmentFile = AttachmentFile(name, detail(kept), kind().symbol, thumbnail, typeLabel())

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
