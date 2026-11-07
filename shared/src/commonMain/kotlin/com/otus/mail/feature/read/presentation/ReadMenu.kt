package com.otus.mail.feature.read.presentation

import com.otus.mail.core.categories.domain.CategoryPlacement
import com.otus.mail.core.categories.domain.MailCategory
import com.otus.mail.core.mailbox.pathText
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.core.messages.domain.isOwn
import com.otus.mail.core.messages.domain.otherReply
import com.otus.mail.core.messages.domain.replyAction
import com.otus.mail.core.messages.presentation.detail
import com.otus.mail.feature.read.domain.Participant

/**
 * The Read view page the ⋯ menu acts on: its [message], at [index] of [count] in its conversation,
 * newest first. [imagesBlocked] while its sender may not show remote images. [suggestedPath] is the
 * folder this sender's mail usually goes to, when one is known. [people] is everyone the
 * conversation names besides the user. [category] is where the Inbox's categories put the
 * conversation; null while they are off or the list is not the Inbox.
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
    val people: List<Participant> = emptyList(),
    val category: CategoryPlacement? = null,
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

/**
 * The ⋯ menu for a page: its [sections], and the actions it [withheld], such as both replies for
 * a newsletter. The menu's last section offers every action it does not [cover][covers] that the
 * bar leaves out.
 */
class ReadMenu(
    val sections: List<ReadMenuSection>,
    val withheld: Set<MailAction>,
) {
    /** Mail's actions in the menu's sections. */
    val placed: Set<MailAction>
        get() =
            sections
                .flatMap { it.entries }
                .filterIsInstance<ReadMenuEntry.Act>()
                .map { it.action }
                .toSet()

    val covers: Set<MailAction>
        get() = placed + withheld
}

/** One ⋯ menu entry: one of Mail's actions, or one only the Read view offers. */
sealed interface ReadMenuEntry {
    /** [action] as the bar would offer it; marks act on the page alone. A [detail] shows under it. */
    data class Act(
        val action: MailAction,
        val detail: String? = null,
    ) : ReadMenuEntry

    data object ViewDetails : ReadMenuEntry

    data object Print : ReadMenuEntry

    /** Deletes only the page's message; offered on older pages of a conversation. */
    data object DeleteThisMessage : ReadMenuEntry

    data class AlwaysShowImages(
        val sender: String,
    ) : ReadMenuEntry

    data object BlockSender : ReadMenuEntry

    /** A switch that stays on until the user turns it off. */
    data class Mute(
        val on: Boolean,
    ) : ReadMenuEntry

    /** Opens what the app offers for the sender: their card, or ways to save the address. */
    data object SenderContact : ReadMenuEntry

    /** Lists everyone the conversation names, so any of them can be saved or opened. */
    data class People(
        val people: List<Participant>,
    ) : ReadMenuEntry

    /** Says why the Inbox shows the conversation in [category], and lets the sender's mail go elsewhere. */
    data class WhyCategory(
        val category: MailCategory,
    ) : ReadMenuEntry

    /** Puts all mail from the sender in a category the user picks. */
    data object CategorizeSender : ReadMenuEntry
}

/**
 * The ⋯ menu for [page]: what acts on its message, then its sender, then the whole conversation.
 * A single message moves from its own section and has no conversation section. A newsletter
 * offers no reply, and the user's own message has no reply or sender section. Always show images
 * appears only while images are blocked, and the category entries only while the Inbox sorts the
 * conversation. People appears once anyone besides the sender takes part: with the conversation's
 * entries in a thread, else with the message's.
 */
fun readMenu(page: ReadPage): ReadMenu =
    ReadMenu(
        buildList {
            add(ReadMenuSection(ReadMenuPart.ThisMessage, page.messageEntries()))
            if (!page.fromUser) {
                val entries =
                    listOf(ReadMenuEntry.SenderContact, ReadMenuEntry.BlockSender, ReadMenuEntry.Act(MailAction.Spam)) +
                        listOfNotNull(ReadMenuEntry.CategorizeSender.takeIf { page.category != null })
                add(ReadMenuSection(ReadMenuPart.Sender(page.senderName), entries))
            }
            if (page.thread) {
                val entries =
                    listOf(page.moveEntry, ReadMenuEntry.Mute(page.muted), ReadMenuEntry.Act(MailAction.Trash)) +
                        listOfNotNull(page.peopleEntry)
                add(ReadMenuSection(ReadMenuPart.Conversation, entries))
            }
        },
        page.withheld,
    )

/**
 * What the menu leaves out: both replies on a newsletter or the user's own message, Reply all
 * while it would reach only the sender, as Reply does, and Spam on the user's own message, whose
 * sender section it lives in.
 */
private val ReadPage.withheld: Set<MailAction>
    get() =
        when {
            !repliable -> setOf(MailAction.Reply, MailAction.ReplyAll)
            message.replyAction(own) == MailAction.Reply -> setOf(MailAction.ReplyAll)
            else -> emptySet()
        } + setOfNotNull(MailAction.Spam.takeIf { fromUser })

private fun ReadPage.messageEntries(): List<ReadMenuEntry> =
    buildList {
        if (imagesBlocked && !fromUser) add(ReadMenuEntry.AlwaysShowImages(senderName))
        if (repliable) message.otherReply(own)?.let { add(ReadMenuEntry.Act(it)) }
        add(ReadMenuEntry.Act(MailAction.Forward))
        add(ReadMenuEntry.Act(MailAction.Read))
        add(ReadMenuEntry.Act(MailAction.Flag))
        if (!thread) add(moveEntry)
        add(ReadMenuEntry.ViewDetails)
        category?.let { add(ReadMenuEntry.WhyCategory(it.category)) }
        add(ReadMenuEntry.Print)
        if (thread && index > 0) add(ReadMenuEntry.DeleteThisMessage)
        if (!thread) peopleEntry?.let(::add)
    }

private val ReadPage.moveEntry: ReadMenuEntry.Act
    get() = ReadMenuEntry.Act(MailAction.Move, suggestedPath?.let { "Usually ${it.pathText()}" })

/** People, once the page names someone besides its sender; the sender alone has its own entry. */
private val ReadPage.peopleEntry: ReadMenuEntry.People?
    get() = ReadMenuEntry.People(people).takeIf { people.size > if (fromUser) 0 else 1 }

private val ReadPage.repliable: Boolean
    get() = !newsletter && !fromUser

private val ReadPage.thread: Boolean
    get() = count > 1

private val ReadPage.fromUser: Boolean
    get() = message.isOwn(own, message.sender)

private val ReadPage.senderName: String
    get() = message.sender.name.ifBlank { message.sender.address }
