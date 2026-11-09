package com.otus.mail.core.messages.presentation.actions

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_schedule_send
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.BarButton
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MessageFlag
import com.otus.mail.core.messages.domain.original
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.messages.presentation.MessageMarks
import com.otus.mail.core.messages.presentation.MoveTarget
import org.jetbrains.compose.resources.DrawableResource

/**
 * What the actions of one place work on. [messages] are the rows a swipe, the selection, or the
 * Read view acts on; empty on a list with nothing selected. [marks] set read and flag marks, the
 * list's own unless a place marks something else, such as one page of a conversation. [moves]
 * picks a folder and suggests one. [page], [list], and [draft] are the open message, the list on
 * screen, and the draft being written, for the places that show them.
 */
internal class ActionSubject(
    val actions: MessageActions?,
    val messages: List<MailMessage> = emptyList(),
    val marks: MessageMarks? = actions?.marks,
    val moves: MoveOptions = MoveOptions(),
    val page: PageSubject? = null,
    val list: ListSubject? = null,
    val draft: DraftSubject? = null,
)

/** How Move picks a folder, through [choose], and where Suggested move goes, the [suggestion]. */
internal class MoveOptions(
    val choose: ChooseFolder = { _, _ -> },
    val suggestion: MoveTarget? = null,
)

/**
 * The open [message] replies answer, in a folder of [role]; drafts start through [onCompose], and
 * a message in Scheduled is edited or sent through [scheduled].
 */
internal class PageSubject(
    val message: MailMessage,
    val role: MailboxRole?,
    val onCompose: (ComposeRequest) -> Unit,
    val scheduled: ScheduledActions? = null,
)

/**
 * The message list with nothing selected: Mark all read marks the unread [results] on screen, and
 * New mail starts through [onCompose] from [accountId] when the list shows one account.
 */
internal class ListSubject(
    val results: List<MailMessage>,
    val accountId: String?,
    val onSearch: () -> Unit,
    val onFilters: () -> Unit,
    val onCompose: (ComposeRequest) -> Unit = {},
)

/** The draft being written: Send and Attach stay unavailable while theirs are null. */
internal class DraftSubject(
    val onSend: (() -> Unit)?,
    val onAttach: (() -> Unit)?,
    val onDiscard: () -> Unit,
    val scheduled: Boolean,
)

/**
 * [button] as it applies here; null when nothing here can take it. Moving needs one account,
 * because folders belong to an account. Suggested move goes to [MoveOptions.suggestion], when
 * there is one. All actions is the bar's own menu, so it is never offered. Every action is named
 * once here, by what it acts on.
 */
internal fun ActionSubject.offer(button: BarButton): OfferedAction? =
    when (button) {
        is BarButton.MoveTo -> actions?.offerMoveTo(messages, button)
        is MailAction -> offer(button)
    }

private fun ActionSubject.offer(action: MailAction): OfferedAction? =
    when (action) {
        MailAction.Read, MailAction.Flag, MailAction.ReadLater, MailAction.Archive, MailAction.Trash,
        MailAction.Spam, MailAction.NotSpam, MailAction.Restore, MailAction.DeleteForever, MailAction.Move,
        MailAction.SuggestedMove, MailAction.RuleForSender,
        -> {
            offerOnMessages(action)
        }

        MailAction.Reply, MailAction.ReplyAll, MailAction.Forward, MailAction.FollowUp -> {
            page?.offer(action)
        }

        MailAction.NewMail -> {
            newMail(action)
        }

        MailAction.Search, MailAction.Filters, MailAction.MarkAllRead, MailAction.EmptyFolder -> {
            list?.let { offerOnList(it, action) }
        }

        MailAction.Send, MailAction.Attach, MailAction.Discard -> {
            draft?.offer(action)
        }

        MailAction.EditScheduled, MailAction.SendNow -> {
            page?.offerScheduled(action)
        }

        MailAction.AllActions -> {
            null
        }
    }

/** The actions on [ActionSubject.messages], which [offer] routes here. */
private fun ActionSubject.offerOnMessages(action: MailAction): OfferedAction? =
    when (action) {
        MailAction.Read -> {
            marks?.offer(messages, MessageFlag.Read)
        }

        MailAction.Flag -> {
            marks?.offer(messages, MessageFlag.Flagged)
        }

        MailAction.ReadLater -> {
            actions?.offerReadLater(messages)
        }

        MailAction.Archive, MailAction.Trash, MailAction.Spam -> {
            actions?.offerFiling(messages, filingRole(action), action)
        }

        MailAction.NotSpam -> {
            actions?.offerNotSpam(messages)
        }

        MailAction.Restore -> {
            actions?.offerRestore(messages)
        }

        MailAction.DeleteForever -> {
            actions?.offerDeleteForever(messages)
        }

        MailAction.Move -> {
            actions?.offerMove(messages, moves.choose)
        }

        MailAction.SuggestedMove -> {
            moves.suggestion?.let { actions?.offerFolder(messages, it) }
        }

        MailAction.RuleForSender -> {
            actions?.offerRule(messages)
        }

        else -> {
            null
        }
    }

/** The folder Archive, Trash, and Spam file into. */
private fun filingRole(action: MailAction): MailboxRole =
    when (action) {
        MailAction.Trash -> MailboxRole.Trash
        MailAction.Spam -> MailboxRole.Junk
        else -> MailboxRole.Archive
    }

/** Replies and forwards of the open page, which [offer] routes here. */
private fun PageSubject.offer(action: MailAction): OfferedAction? {
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

/** The list's own actions, which [offer] routes here. */
private fun ActionSubject.offerOnList(
    list: ListSubject,
    action: MailAction,
): OfferedAction? =
    when (action) {
        MailAction.Search -> plainOffer(action, list.onSearch)
        MailAction.Filters -> plainOffer(action, list.onFilters)
        MailAction.MarkAllRead -> offerMarkAllRead(list)
        MailAction.EmptyFolder -> actions?.offerEmpty()
        else -> null
    }

/** The draft's actions, which [offer] routes here. */
private fun DraftSubject.offer(action: MailAction): OfferedAction? =
    when (action) {
        MailAction.Send -> {
            val symbol = if (scheduled) Res.drawable.symbol_schedule_send else action.symbol
            onSend?.let { draftOffer(action, it, symbol) }
        }

        MailAction.Attach -> {
            onAttach?.let { draftOffer(action, it) }
        }

        MailAction.Discard -> {
            draftOffer(action, onDiscard)
        }

        else -> {
            null
        }
    }

/** New mail from the open message's account, or the list's; a list of all accounts lets compose pick. */
private fun ActionSubject.newMail(button: BarButton): OfferedAction? =
    when {
        page != null -> plainOffer(button) { page.onCompose(ComposeRequest.New(page.message.location.accountId)) }
        list != null -> plainOffer(button) { list.onCompose(ComposeRequest.New(list.accountId)) }
        else -> null
    }

private fun ActionSubject.offerMarkAllRead(list: ListSubject): OfferedAction? {
    val marks = marks ?: return null
    val unread = list.results.filter { it.attributes.unread && marks.can(it, MessageFlag.Read) }
    return unread.takeIf { it.isNotEmpty() }?.let {
        plainOffer(MailAction.MarkAllRead) { marks.set(unread, MessageFlag.Read, true) }
    }
}

/** A draft's action, which leaves the compose page. */
private fun draftOffer(
    button: BarButton,
    run: () -> Unit,
    symbol: DrawableResource = button.symbol,
) = OfferedAction(button.buttonLabel(), symbol, leaves = true, run = run)
