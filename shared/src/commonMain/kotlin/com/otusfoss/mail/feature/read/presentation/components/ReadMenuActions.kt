package com.otusfoss.mail.feature.read.presentation.components

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_all_inbox
import com.otusfoss.core.designsystem.resources.symbol_block
import com.otusfoss.core.designsystem.resources.symbol_delete
import com.otusfoss.core.designsystem.resources.symbol_group
import com.otusfoss.core.designsystem.resources.symbol_info
import com.otusfoss.core.designsystem.resources.symbol_label
import com.otusfoss.core.designsystem.resources.symbol_notifications_off
import com.otusfoss.core.designsystem.resources.symbol_person_add
import com.otusfoss.core.designsystem.resources.symbol_print
import com.otusfoss.core.designsystem.resources.symbol_verified_user
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.feedback.components.ToastMessage
import com.otusfoss.core.designsystem.shared.overlays.components.OptionsItem
import com.otusfoss.mail.core.categories.presentation.label
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageContent
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.MessageMarks
import com.otusfoss.mail.core.messages.presentation.actions.ActionSubject
import com.otusfoss.mail.core.messages.presentation.actions.OfferedAction
import com.otusfoss.mail.core.messages.presentation.actions.offer
import com.otusfoss.mail.feature.read.presentation.ReadContext
import com.otusfoss.mail.feature.read.presentation.ReadMenu
import com.otusfoss.mail.feature.read.presentation.ReadMenuEntry
import com.otusfoss.mail.feature.read.presentation.ReadSheet
import com.otusfoss.mail.feature.read.presentation.ReadState
import com.otusfoss.mail.feature.read.presentation.title
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
    private val headers = read.services.headers != null

    /** Whether the app offers something for a person: their card, or ways to save the address. */
    private val people = read.services.person != null

    /** Whether the list sorts into the Inbox's categories, which the category sheet needs. */
    private val sorted = read.services.categories != null

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
                val details = ReadSheet.Details(page)
                sheetItem("View details", Res.drawable.symbol_info, details, headers, "Headers, encryption, original")
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
                sheetItem("Add to contacts", Res.drawable.symbol_person_add, ReadSheet.Person(page.sender), people)
            }

            is ReadMenuEntry.People -> {
                val sheet = ReadSheet.People(entry.people)
                sheetItem("People (${entry.people.size})", Res.drawable.symbol_group, sheet, people)
            }

            is ReadMenuEntry.WhyCategory -> {
                val label = "Why is this in ${entry.category.label}?"
                sheetItem(label, Res.drawable.symbol_all_inbox, ReadSheet.Category, sorted)
            }

            ReadMenuEntry.CategorizeSender -> {
                sheetItem("Categorize sender…", Res.drawable.symbol_label, ReadSheet.Category, sorted, opens = true)
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

    /** An entry that opens [sheet], where the app [offers] what the sheet needs; null where it does not. */
    private fun sheetItem(
        label: String,
        symbol: DrawableResource,
        sheet: ReadSheet,
        offers: Boolean,
        detail: String? = null,
        opens: Boolean = false,
    ): OptionsItem? =
        OptionsItem.Action(UiAction(label, symbol) { state.sheet = sheet }, opens, detail).takeIf { offers }

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
