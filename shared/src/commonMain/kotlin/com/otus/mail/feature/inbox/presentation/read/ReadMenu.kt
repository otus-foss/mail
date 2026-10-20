package com.otus.mail.feature.inbox.presentation.read

import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.domain.isOwn
import com.otus.mail.feature.inbox.domain.otherReply

/**
 * The Read view page the ⋯ menu acts on: its [message], at [index] of [count] in its conversation,
 * newest first. [imagesBlocked] while its sender may not show remote images. [suggestedPath] is the
 * folder this sender's mail usually goes to, when one is known.
 */
data class ReadPage(
    val message: MailMessage,
    val own: OwnAddresses,
    val index: Int = 0,
    val count: Int = 1,
    val newsletter: Boolean = false,
    val imagesBlocked: Boolean = false,
    val muted: Boolean = false,
    val suggestedPath: List<String>? = null,
)

/** A part of the ⋯ menu: the page's message, its sender, or the whole conversation. */
sealed interface ReadMenuPart {
    data object ThisMessage : ReadMenuPart

    data class Sender(
        val name: String,
    ) : ReadMenuPart

    data object Conversation : ReadMenuPart
}

data class ReadMenuSection(
    val part: ReadMenuPart,
    val entries: List<ReadMenuEntry>,
)

/** One ⋯ menu entry. The screen maps each to its icon and action. */
sealed interface ReadMenuEntry {
    /** The reply the big button does not cover; [name] is the person a plain reply goes to. */
    data class OtherReply(
        val action: MailAction,
        val name: String,
    ) : ReadMenuEntry

    data object Forward : ReadMenuEntry

    data object MarkUnread : ReadMenuEntry

    data class Flag(
        val flagged: Boolean,
    ) : ReadMenuEntry

    data object ViewDetails : ReadMenuEntry

    data object Print : ReadMenuEntry

    /** Deletes only the page's message; offered on older pages of a conversation. */
    data object DeleteThisMessage : ReadMenuEntry

    data class AlwaysShowImages(
        val sender: String,
    ) : ReadMenuEntry

    data object BlockSender : ReadMenuEntry

    data object ReportSpam : ReadMenuEntry

    data class MoveTo(
        val suggestedPath: List<String>?,
    ) : ReadMenuEntry

    /** A switch that stays on until the user turns it off. */
    data class Mute(
        val on: Boolean,
    ) : ReadMenuEntry

    data object DeleteConversation : ReadMenuEntry
}

/**
 * The ⋯ menu for [page]: what acts on its message, then its sender, then the whole conversation.
 * A single message moves from its own section and has no conversation section. A newsletter
 * offers no reply, and the user's own message has no reply or sender section. Always show images
 * appears only while images are blocked.
 */
fun readMenu(page: ReadPage): List<ReadMenuSection> =
    buildList {
        add(ReadMenuSection(ReadMenuPart.ThisMessage, page.messageEntries()))
        if (!page.fromUser) {
            val entries = listOf(ReadMenuEntry.BlockSender, ReadMenuEntry.ReportSpam)
            add(ReadMenuSection(ReadMenuPart.Sender(page.senderName), entries))
        }
        if (page.thread) {
            val entries =
                listOf(
                    ReadMenuEntry.MoveTo(page.suggestedPath),
                    ReadMenuEntry.Mute(page.muted),
                    ReadMenuEntry.DeleteConversation,
                )
            add(ReadMenuSection(ReadMenuPart.Conversation, entries))
        }
    }

private fun ReadPage.messageEntries(): List<ReadMenuEntry> =
    buildList {
        if (imagesBlocked && !fromUser) add(ReadMenuEntry.AlwaysShowImages(senderName))
        if (!newsletter && !fromUser) {
            message.otherReply(own)?.let { add(ReadMenuEntry.OtherReply(it, firstName)) }
        }
        add(ReadMenuEntry.Forward)
        add(ReadMenuEntry.MarkUnread)
        add(ReadMenuEntry.Flag(message.attributes.flagged))
        if (!thread) add(ReadMenuEntry.MoveTo(suggestedPath))
        add(ReadMenuEntry.ViewDetails)
        add(ReadMenuEntry.Print)
        if (thread && index > 0) add(ReadMenuEntry.DeleteThisMessage)
    }

private val ReadPage.thread: Boolean
    get() = count > 1

private val ReadPage.fromUser: Boolean
    get() = message.isOwn(own, message.sender)

private val ReadPage.senderName: String
    get() = message.sender.name.ifBlank { message.sender.address }

private val ReadPage.firstName: String
    get() = senderName.substringBefore(' ')
