package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import com.otusfoss.core.designsystem.shared.feedback.components.ToastMessage
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.inJunk
import com.otusfoss.mail.core.messages.domain.isOwn
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.actions.ActionSwitch
import com.otusfoss.mail.core.messages.presentation.actions.PageExtras
import com.otusfoss.mail.feature.read.presentation.NewsletterStep
import com.otusfoss.mail.feature.read.presentation.ReadContext

/** What the Read view's strips do, with links opening through the platform's handler. */
@Composable
internal fun rememberStripActions(read: ReadContext): ReadStripActions {
    val uriHandler = LocalUriHandler.current
    return remember(read) { ReadStripActions(read) { openLink(uriHandler::openUri, it) } }
}

/**
 * What only the Read view can do with [page], for its bar and ⋯ menu: mute the conversation,
 * show or hide the remote images [look] blocks, block the sender, and leave the page's mailing
 * list through [strips]. Each is null where the page cannot take it.
 */
internal fun ReadParts.pageExtras(
    page: MailMessage,
    look: ReadPageLook,
    strips: ReadStripActions,
): PageExtras =
    PageExtras(
        mute = muteSwitch(),
        images = look.imagesSwitch(page, state.bodies.body(page)),
        onBlock = blockSender(page),
        onUnsubscribe = unsubscribe(page, look, strips),
    )

/**
 * Muting the conversation, once the view knows whether it is muted. The switch flips at once and
 * goes back, with a toast, when the server refuses.
 */
private fun ReadParts.muteSwitch(): ActionSwitch? {
    val edits = read.services.edits
    val muted = state.muted
    if (edits == null || muted == null) return null
    return ActionSwitch(muted) { on ->
        state.muted = on
        read.effects.launch {
            if (!edits.setMuted(row, on)) {
                state.muted = !on
                read.effects.toasts.show(ToastMessage("Couldn't change mute. Try again."))
            }
        }
    }
}

/** Sends mail from [page]'s sender to Spam from now on, and says how it went; null for the user's own message. */
private fun ReadParts.blockSender(page: MailMessage): (() -> Unit)? {
    val blocks = read.services.sender.blocks
    if (blocks == null || page.isOwn(read.replies.own, page.sender)) return null
    return {
        val name = page.sender.shownName
        read.effects.launch {
            val blocked = blocks.block(page)
            val text = if (blocked) "Mail from $name now goes to Spam" else "Couldn't block $name. Try again."
            read.effects.toasts.show(ToastMessage(text))
        }
    }
}

/**
 * Leaves [page]'s mailing list, as its newsletter strip does, also on a page of a conversation,
 * which shows no such strip. Null once it is left, in Spam, or for mail from no list. The toast
 * names the list as the message does, or else its sender.
 */
private fun ReadParts.unsubscribe(
    page: MailMessage,
    look: ReadPageLook,
    strips: ReadStripActions,
): (() -> Unit)? {
    val content = content(page)
    val how = content?.unsubscribe?.takeUnless { inJunk(page.location.mailboxIds, look.folders) }
    if (how == null || strips.step(page) != NewsletterStep.Subscribed) return null
    return { strips.unsubscribe(page, how, content.listName ?: page.sender.shownName) }
}
