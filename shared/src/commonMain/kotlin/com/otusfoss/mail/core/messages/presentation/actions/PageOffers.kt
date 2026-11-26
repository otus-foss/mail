package com.otusfoss.mail.core.messages.presentation.actions

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_hide_image
import com.otusfoss.core.designsystem.resources.symbol_notifications
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.original
import org.jetbrains.compose.resources.DrawableResource

/** A switch an action flips: whether it is [on], and [set], which turns it on or off. */
internal class ActionSwitch(
    val on: Boolean,
    val set: (Boolean) -> Unit,
)

/**
 * What only the open page's screen can do with it; each is null where the page cannot take it.
 * [mute] stops alerts for its conversation, [images] shows its blocked remote images this time,
 * [onBlock] sends its sender's mail to Spam from now on, and [onUnsubscribe] leaves its mailing list.
 */
internal class PageExtras(
    val mute: ActionSwitch? = null,
    val images: ActionSwitch? = null,
    val onBlock: (() -> Unit)? = null,
    val onUnsubscribe: (() -> Unit)? = null,
)

/** The open page's replies, forwards, and [PageSubject.extras], which [offer] routes here. */
internal fun PageSubject.offer(action: MailAction): OfferedAction? =
    when (action) {
        MailAction.Mute -> extras.mute?.offer(action, ::muteLabel, Res.drawable.symbol_notifications)
        MailAction.ShowImages -> extras.images?.offer(action, ::imagesLabel, Res.drawable.symbol_hide_image)
        MailAction.BlockSender -> extras.onBlock?.let { plainOffer(action, it) }
        MailAction.Unsubscribe -> extras.onUnsubscribe?.let { plainOffer(action, it) }
        else -> offerReply(action)
    }

private fun PageSubject.offerReply(action: MailAction): OfferedAction? {
    val request =
        when (action) {
            MailAction.Reply -> ComposeRequest.Reply(message.original(), all = false)
            MailAction.ReplyAll -> ComposeRequest.Reply(message.original(), all = true)
            MailAction.Forward -> ComposeRequest.Forward(message.original())
            MailAction.FollowUp -> ComposeRequest.FollowUp(message.original()).takeIf { role == MailboxRole.Sent }
            else -> null
        }
    return request?.let { plainOffer(action) { onCompose(it) } }
}

/**
 * This switch as [action]'s button, whose [label] says what pressing it does next. While the
 * switch is on, the button shows the [off] symbol.
 */
private fun ActionSwitch.offer(
    action: MailAction,
    label: (on: Boolean) -> String,
    off: DrawableResource,
): OfferedAction = OfferedAction(label(on), if (on) off else action.symbol, leaves = false) { set(!on) }
