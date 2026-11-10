package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.SendIdentity
import com.otus.mail.core.messages.domain.Signature
import com.otus.mail.core.messages.domain.signature

/**
 * Whether Write opens this request signed. A new message is; a reply or forward follows
 * [inReplies]. A resumed copy keeps its signature. An unsigned copy whose From identity, among
 * [identities], has a signature had it turned off and stays unsigned; otherwise nothing tells, so
 * a copy without a quote counts as a new message and one with a quote follows [inReplies].
 */
internal fun ComposeRequest.startsSigned(
    inReplies: Boolean,
    identities: List<SendIdentity>,
): Boolean =
    when (this) {
        is ComposeRequest.New -> {
            true
        }

        is ComposeRequest.Resume -> {
            when {
                !draft.signature.isEmpty -> true
                identities.signatureOf(draft.from)?.isEmpty == false -> false
                else -> draft.quote.isBlank() || inReplies
            }
        }

        else -> {
            inReplies
        }
    }

/** The signature a resumed copy carries, which the draft keeps while its From identity is unknown. */
internal val ComposeRequest.resumedSignature: Signature
    get() = (this as? ComposeRequest.Resume)?.draft?.signature ?: Signature()

/** The signature of [from]'s identity; null when none of these is [from]. */
internal fun List<SendIdentity>.signatureOf(from: MailSender?): Signature? =
    firstOrNull {
        from != null && it.accountId == from.accountKey && it.sender.address.equals(from.address, ignoreCase = true)
    }?.signature
