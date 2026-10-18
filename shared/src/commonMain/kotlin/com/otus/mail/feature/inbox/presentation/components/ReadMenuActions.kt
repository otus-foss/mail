package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.components.ToastMessage
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
 * What the ⋯ menu's entries do. Entries about this message act on the page's message alone; the
 * conversation's act on the [row], the whole conversation the list shows. An action that takes the
 * row away runs through [perform], which closes the page first.
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
            is ReadMenuEntry.OtherReply -> reply(page.withContent(content), entry.action)
            ReadMenuEntry.Forward -> reply(page.withContent(content), MailAction.Forward)
            ReadMenuEntry.MarkUnread -> mark(page, MessageFlag.Read, on = page.attributes.unread)
            is ReadMenuEntry.Flag -> mark(page, MessageFlag.Flagged, on = !entry.flagged)
            ReadMenuEntry.ViewDetails -> state.sheet = ReadSheet.Details(page)
            ReadMenuEntry.Print -> read.files.printer?.print(page.subject, printable(page, content))
            ReadMenuEntry.DeleteThisMessage -> deletePage(page)
            is ReadMenuEntry.AlwaysShowImages -> read.privacy.allowSender(page.sender.address)
            ReadMenuEntry.BlockSender -> block(page)
            ReadMenuEntry.ReportSpam -> rowAction(MailAction.Spam)
            is ReadMenuEntry.MoveTo -> rowAction(MailAction.Move)
            is ReadMenuEntry.Mute -> mute(!entry.on)
            ReadMenuEntry.DeleteConversation -> rowAction(MailAction.Trash)
        }
    }

    private fun canRun(entry: ReadMenuEntry): Boolean =
        when (entry) {
            ReadMenuEntry.Print -> read.files.printer != null
            ReadMenuEntry.ViewDetails -> read.services.headers != null
            ReadMenuEntry.BlockSender -> read.services.blocks != null
            is ReadMenuEntry.Mute -> read.services.mute != null && state.muted != null
            ReadMenuEntry.MarkUnread -> actions?.canMark(row, MessageFlag.Read) == true
            is ReadMenuEntry.Flag -> actions?.canMark(row, MessageFlag.Flagged) == true
            ReadMenuEntry.DeleteThisMessage -> actions != null
            ReadMenuEntry.ReportSpam -> offer(MailAction.Spam) != null
            is ReadMenuEntry.MoveTo -> offer(MailAction.Move) != null
            ReadMenuEntry.DeleteConversation -> offer(MailAction.Trash) != null
            else -> true
        }

    private fun offer(action: MailAction): OfferedAction? = actions?.offer(listOf(row), action, chooseFolder)

    private fun rowAction(action: MailAction) {
        offer(action)?.let(perform)
    }

    private fun reply(
        page: MailMessage,
        action: MailAction,
    ) {
        replyOffer(page, action, role = null, compose)?.run?.invoke()
    }

    private fun mark(
        page: MailMessage,
        flag: MessageFlag,
        on: Boolean,
    ) {
        actions?.onMark?.invoke(listOf(page), flag, on)
        // The row's own email shows the list's marks, which a refused change puts back.
        if (page.emailId != row.emailId) state.edit(page.id) { it.withFlag(flag, on) }
    }

    /** Moves the page's message alone to Trash, with the usual Undo toast. */
    private fun deletePage(page: MailMessage) {
        val moves = actions ?: return
        val target = moves.shortcutTarget(page, FolderShortcut.Trash) ?: return
        state.drop(page.id)
        moves.moves.onMove(page, target)
    }

    private fun block(page: MailMessage) {
        val blocks = read.services.blocks ?: return
        val name = page.sender.name.ifBlank { page.sender.address }
        read.effects.launch {
            val text =
                if (blocks.block(
                        page,
                    )
                ) {
                    "Mail from $name now goes to Spam"
                } else {
                    "Couldn't block $name. Try again."
                }
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
