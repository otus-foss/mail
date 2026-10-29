package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageContent
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.withContent
import com.otus.mail.feature.inbox.domain.withFlag
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.ReadContext
import com.otus.mail.feature.inbox.presentation.ReadSheet
import com.otus.mail.feature.inbox.presentation.ReadState
import com.otus.mail.feature.inbox.presentation.read.ReadMenuEntry
import com.otus.mail.feature.inbox.presentation.read.ReadMenuSection

/**
 * What the ⋯ menu's entries do. Entries about this message act on the page's email alone, through
 * the read context's page changes; the conversation's act on the [row], the whole conversation the
 * list shows. An action that takes the row away runs through [perform], which closes the page first.
 */
internal class ReadMenuActions(
    private val row: MailMessage,
    private val actions: MessageActions?,
    private val read: ReadContext,
    private val state: ReadState,
    private val compose: (ComposeRequest) -> Unit,
    private val perform: (OfferedAction) -> Unit,
    private val chooseFolder: ChooseFolder,
) {
    /** [sections] without what nothing here can do, such as Print where the platform cannot. */
    fun available(sections: List<ReadMenuSection>): List<ReadMenuSection> =
        sections
            .map { section -> section.copy(entries = section.entries.filter(::canRun)) }
            .filter { it.entries.isNotEmpty() }

    fun run(
        entry: ReadMenuEntry,
        page: MailMessage,
        content: MessageContent?,
    ) {
        when (entry) {
            is ReadMenuEntry.OtherReply -> {
                reply(page.withContent(content), entry.action)
            }

            ReadMenuEntry.Forward -> {
                reply(page.withContent(content), MailAction.Forward)
            }

            ReadMenuEntry.MarkUnread -> {
                mark(page, MessageFlag.Read, on = page.attributes.unread)
            }

            is ReadMenuEntry.Flag -> {
                mark(page, MessageFlag.Flagged, on = !entry.flagged)
            }

            ReadMenuEntry.ViewDetails, ReadMenuEntry.SenderContact, is ReadMenuEntry.People -> {
                state.sheet = sheet(entry, page)
            }

            ReadMenuEntry.Print -> {
                read.files.printer?.print(page.subject, printable(page, content))
            }

            ReadMenuEntry.DeleteThisMessage -> {
                deletePage(page)
            }

            is ReadMenuEntry.AlwaysShowImages -> {
                read.privacy.allowSender(page.sender.address)
            }

            ReadMenuEntry.BlockSender -> {
                block(page)
            }

            ReadMenuEntry.ReportSpam -> {
                offer(MailAction.Spam)?.let(perform)
            }

            is ReadMenuEntry.MoveTo -> {
                offer(MailAction.Move)?.let(perform)
            }

            is ReadMenuEntry.Mute -> {
                mute(!entry.on)
            }

            ReadMenuEntry.DeleteConversation -> {
                offer(MailAction.Trash)?.let(perform)
            }
        }
    }

    private fun canRun(entry: ReadMenuEntry): Boolean =
        when (entry) {
            ReadMenuEntry.Print -> read.files.printer != null
            ReadMenuEntry.ViewDetails -> read.services.headers != null
            ReadMenuEntry.BlockSender -> read.services.blocks != null
            is ReadMenuEntry.Mute -> read.services.mute != null && state.muted != null
            ReadMenuEntry.MarkUnread -> canMarkPage(MessageFlag.Read)
            is ReadMenuEntry.Flag -> canMarkPage(MessageFlag.Flagged)
            ReadMenuEntry.DeleteThisMessage -> canChangePages
            ReadMenuEntry.ReportSpam -> offer(MailAction.Spam) != null
            is ReadMenuEntry.MoveTo -> offer(MailAction.Move) != null
            ReadMenuEntry.DeleteConversation -> offer(MailAction.Trash) != null
            ReadMenuEntry.SenderContact, is ReadMenuEntry.People -> read.services.person != null
            else -> true
        }

    private val canChangePages: Boolean get() = read.services.changes != null && actions != null

    private fun canMarkPage(flag: MessageFlag): Boolean = canChangePages && actions?.canMark(row, flag) == true

    private fun offer(action: MailAction): OfferedAction? = actions?.offer(listOf(row), action, chooseFolder)

    private fun reply(
        page: MailMessage,
        action: MailAction,
    ) {
        replyOffer(page, action, role = null, compose)?.run?.invoke()
    }

    /** Marks the page's email alone, with its own marks; a refused change goes back. */
    private fun mark(
        page: MailMessage,
        flag: MessageFlag,
        on: Boolean,
    ) {
        val changes = read.services.changes ?: return
        state.edit(page.id) { it.withFlag(flag, on) }
        changes.mark(row, page, flag, on) { state.edit(page.id) { it.withFlag(flag, !on) } }
    }

    /**
     * Moves the page's email alone to Trash, with the usual Undo toast. The conversation stays on
     * the list, and the page comes back where it was when the server refuses or Undo runs.
     */
    private fun deletePage(page: MailMessage) {
        val changes = read.services.changes ?: return
        val target = actions?.shortcutTarget(page, FolderShortcut.Trash) ?: return
        val index = state.pages.indexOfFirst { it.id == page.id }
        state.drop(page.id)
        changes.trash(row, page, target) { state.restore(page, index) }
    }

    private fun block(page: MailMessage) {
        val blocks = read.services.blocks ?: return
        val name = page.sender.shownName
        read.effects.launch {
            val blocked = blocks.block(page)
            val text = if (blocked) "Mail from $name now goes to Spam" else "Couldn't block $name. Try again."
            read.effects.toasts.show(ToastMessage(text))
        }
    }

    private fun mute(on: Boolean) {
        val mute = read.services.mute ?: return
        state.muted = on
        read.effects.launch {
            if (!mute.setMuted(row, on)) {
                state.muted = !on
                read.effects.toasts.show(ToastMessage("Couldn't change mute. Try again."))
            }
        }
    }
}

/** The page as a printer gets it: its HTML, or its text made safe as HTML. */
private fun printable(
    page: MailMessage,
    content: MessageContent?,
): String =
    content?.html?.html
        ?: (content?.text ?: page.body)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")

/** The sheet an entry opens over [page]: its headers, the sender's contact, or everyone in it. */
private fun sheet(
    entry: ReadMenuEntry,
    page: MailMessage,
): ReadSheet? =
    when (entry) {
        ReadMenuEntry.ViewDetails -> ReadSheet.Details(page)
        ReadMenuEntry.SenderContact -> ReadSheet.Person(page.sender)
        is ReadMenuEntry.People -> ReadSheet.People(entry.people)
        else -> null
    }
