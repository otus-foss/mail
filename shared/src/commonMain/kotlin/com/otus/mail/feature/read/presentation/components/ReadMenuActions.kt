package com.otus.mail.feature.read.presentation.components

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_block
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_info
import com.otus.designsystem.resources.symbol_notifications_off
import com.otus.designsystem.resources.symbol_person_add
import com.otus.designsystem.resources.symbol_print
import com.otus.designsystem.resources.symbol_verified_user
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.overlays.components.OptionsItem
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MessageContent
import com.otus.mail.core.messages.domain.shownName
import com.otus.mail.core.messages.presentation.MessageMarks
import com.otus.mail.core.messages.presentation.actions.ActionSubject
import com.otus.mail.core.messages.presentation.actions.OfferedAction
import com.otus.mail.core.messages.presentation.actions.offer
import com.otus.mail.feature.read.presentation.ReadContext
import com.otus.mail.feature.read.presentation.ReadMenu
import com.otus.mail.feature.read.presentation.ReadMenuEntry
import com.otus.mail.feature.read.presentation.ReadSheet
import com.otus.mail.feature.read.presentation.ReadState
import com.otus.mail.feature.read.presentation.title
import org.jetbrains.compose.resources.DrawableResource

/**
 * The ⋯ menu's rows for the [page] on screen, with its loaded [content]. Mail's actions come from
 * the Read view's [subject] as the bar offers them, acting on the whole conversation, except that
 * marks act on the page's email alone. Each runs through [perform], which closes the page first
 * when the action takes the row away. An entry nothing here can run is left out.
 */
internal class ReadMenuActions(
    private val subject: ActionSubject,
    private val page: MailMessage,
    private val content: MessageContent?,
    private val read: ReadContext,
    private val state: ReadState,
    private val perform: (OfferedAction) -> Unit,
) {
    private val row = subject.messages.single()
    private val edits = read.services.edits

    /** Marks on the page alone, while the conversation's folder lets them change. */
    private val pageSubject =
        ActionSubject(
            subject.actions,
            listOf(page),
            marks =
                subject.marks?.takeIf { edits != null }?.let { marks ->
                    MessageMarks(
                        can = { _, flag -> marks.can(row, flag) },
                        set = { _, flag, on -> edits?.mark(row, page, flag, on) },
                    )
                },
        )

    /** The [menu]'s sections, each after a line and its caption, then [more], what the bar leaves out. */
    fun items(
        menu: ReadMenu,
        more: List<OfferedAction>,
    ): List<OptionsItem> =
        buildList {
            menu.sections
                .map { section -> section.part.title() to section.entries.mapNotNull(::item) }
                .filter { (_, items) -> items.isNotEmpty() }
                .forEachIndexed { index, (title, items) ->
                    if (index > 0) add(OptionsItem.Divider)
                    title?.let { add(OptionsItem.Caption(it)) }
                    addAll(items)
                }
            if (more.isNotEmpty()) add(OptionsItem.Divider)
            addAll(more.map(::item))
        }

    private fun item(
        offered: OfferedAction,
        detail: String? = null,
    ) = OptionsItem.Action(UiAction(offered.label, offered.symbol) { perform(offered) }, offered.asks, detail)

    private fun item(entry: ReadMenuEntry): OptionsItem? =
        when (entry) {
            is ReadMenuEntry.Act -> {
                offer(entry.action)?.let { item(it, entry.detail) }
            }

            ReadMenuEntry.ViewDetails -> {
                read.services.headers?.let {
                    action("View details", Res.drawable.symbol_info, "Headers, encryption, original") {
                        state.sheet = ReadSheet.Details(page)
                    }
                }
            }

            ReadMenuEntry.Print -> {
                print()
            }

            ReadMenuEntry.DeleteThisMessage -> {
                deleteThisMessage()
            }

            is ReadMenuEntry.AlwaysShowImages -> {
                action("Always show images from ${entry.sender}", Res.drawable.symbol_verified_user) {
                    read.privacy.allowSender(page.sender.address)
                }
            }

            ReadMenuEntry.BlockSender -> {
                read.services.blocks?.let { action("Block sender", Res.drawable.symbol_block, run = ::block) }
            }

            is ReadMenuEntry.Mute -> {
                mute(entry)
            }

            ReadMenuEntry.SenderContact -> {
                personItem("Add to contacts", Res.drawable.symbol_person_add, ReadSheet.Person(page.sender))
            }

            is ReadMenuEntry.People -> {
                personItem("People (${entry.people.size})", Res.drawable.symbol_group, ReadSheet.People(entry.people))
            }
        }

    private fun print(): OptionsItem? =
        read.services.files.printer?.let { printer ->
            action("Print or save as PDF", Res.drawable.symbol_print) {
                printer.print(page.subject, printable(page, content))
            }
        }

    private fun deleteThisMessage(): OptionsItem? =
        subject.actions?.shortcutTarget(page, MailboxRole.Trash)?.takeIf { edits != null }?.let { target ->
            action("Delete this message", Res.drawable.symbol_delete, "Only this one, not the thread") {
                edits?.trash(row, page, target)
            }
        }

    private fun mute(entry: ReadMenuEntry.Mute): OptionsItem? =
        state.muted?.takeIf { edits != null }?.let {
            OptionsItem.Toggle("Mute conversation", Res.drawable.symbol_notifications_off, entry.on) {
                mute(!entry.on)
            }
        }

    /** An entry that opens [sheet] about people, where the app offers something for them. */
    private fun personItem(
        label: String,
        symbol: DrawableResource,
        sheet: ReadSheet,
    ): OptionsItem? = read.services.person?.let { action(label, symbol) { state.sheet = sheet } }

    private fun offer(action: MailAction): OfferedAction? =
        if (action == MailAction.Read || action == MailAction.Flag) pageSubject.offer(action) else subject.offer(action)

    private fun block() {
        val blocks = read.services.blocks ?: return
        val name = page.sender.shownName
        read.effects.launch {
            val blocked = blocks.block(page)
            val text = if (blocked) "Mail from $name now goes to Spam" else "Couldn't block $name. Try again."
            read.effects.toasts.show(ToastMessage(text))
        }
    }

    private fun mute(on: Boolean) {
        val edits = edits ?: return
        state.muted = on
        read.effects.launch {
            if (!edits.setMuted(row, on)) {
                state.muted = !on
                read.effects.toasts.show(ToastMessage("Couldn't change mute. Try again."))
            }
        }
    }
}

private fun action(
    label: String,
    symbol: DrawableResource,
    detail: String? = null,
    run: () -> Unit,
) = OptionsItem.Action(UiAction(label, symbol, onClick = run), detail = detail)

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
