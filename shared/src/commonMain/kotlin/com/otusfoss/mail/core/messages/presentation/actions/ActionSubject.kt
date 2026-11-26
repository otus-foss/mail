package com.otusfoss.mail.core.messages.presentation.actions

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_schedule_send
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.MessageFlag
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.MessageMarks
import com.otusfoss.mail.core.messages.presentation.MoveTarget
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
 * a message in Scheduled is edited or sent through [scheduled]. [extras] is what only its screen
 * can do with it, such as muting its conversation.
 */
internal class PageSubject(
    val message: MailMessage,
    val role: MailboxRole?,
    val onCompose: (ComposeRequest) -> Unit,
    val scheduled: ScheduledActions? = null,
    val extras: PageExtras = PageExtras(),
)

/**
 * The message list with nothing selected: Mark all read marks the unread [results] on screen, and
 * New mail starts through [onCompose] from [accountId] when the list shows one account. [extras]
 * is what only its screen can do, such as flipping a filter.
 */
internal class ListSubject(
    val results: List<MailMessage>,
    val accountId: String?,
    val onSearch: () -> Unit,
    val onFilters: () -> Unit,
    val onCompose: (ComposeRequest) -> Unit = {},
    val extras: ListExtras = ListExtras(),
)

/**
 * The draft being written: Send, Attach, and Schedule send stay unavailable while theirs are null.
 * [onSchedule] opens the choice of a send time, and [scheduled] says one is chosen.
 */
internal class DraftSubject(
    val onSend: (() -> Unit)?,
    val onAttach: (() -> Unit)?,
    val onDiscard: () -> Unit,
    val scheduled: Boolean,
    val onSchedule: (() -> Unit)? = null,
)

/**
 * [button] as it applies here; null when nothing here can take it. Moving needs one account,
 * because folders belong to an account. Suggested move goes to [MoveOptions.suggestion], when
 * there is one. All actions is the bar's own menu, so it is never offered. Every action is named
 * once here, by what it acts on.
 */
internal fun ActionSubject.offer(button: BarButton): OfferedAction? =
    when (button) {
        is BarButton.MoveTo -> actions?.takeUnless { holdsScheduled }?.offerMoveTo(messages, button)
        is BarButton.LabelAs -> actions?.takeUnless { holdsScheduled }?.offerLabel(messages, button)
        is BarButton.Chain -> offerChain(button).takeUnless { holdsScheduled }
        is BarButton.GoTo -> list?.offerGoTo(button)
        is BarButton.Filter -> list?.extras?.filters?.offer(button)
        is BarButton.WriteTo -> newMail(button, button.addresses.map { MailSender(name = "", address = it) })
        is MailAction -> offer(button)
    }

/** Whether a message here waits in Scheduled. Filing it would leave its send running; Edit and Send now handle it. */
private val ActionSubject.holdsScheduled: Boolean
    get() = messages.any { it.location.mailboxRole == MailboxRole.Scheduled }

private fun ActionSubject.offer(action: MailAction): OfferedAction? =
    when (action) {
        MailAction.Read, MailAction.Flag, MailAction.ReadLater, MailAction.Archive, MailAction.Trash,
        MailAction.Spam, MailAction.NotSpam, MailAction.Restore, MailAction.DeleteForever, MailAction.Move,
        MailAction.SuggestedMove, MailAction.RuleForSender,
        -> {
            if (holdsScheduled) null else offerOnMessages(action)
        }

        MailAction.Reply, MailAction.ReplyAll, MailAction.Forward, MailAction.FollowUp, MailAction.Unsubscribe,
        MailAction.Mute, MailAction.BlockSender, MailAction.ShowImages,
        -> {
            page?.offer(action)
        }

        MailAction.NewMail -> {
            newMail(action)
        }

        MailAction.Search, MailAction.Filters, MailAction.MarkAllRead, MailAction.SelectAll, MailAction.EmptyFolder -> {
            list?.let { offerOnList(it, action) }
        }

        MailAction.Send, MailAction.ScheduleSend, MailAction.Attach, MailAction.Discard -> {
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

/** The draft's actions, which [offer] routes here. */
private fun DraftSubject.offer(action: MailAction): OfferedAction? =
    when (action) {
        MailAction.Send -> {
            val symbol = if (scheduled) Res.drawable.symbol_schedule_send else action.symbol
            onSend?.let { draftOffer(action, it, symbol) }
        }

        MailAction.ScheduleSend -> {
            val label = scheduleLabel(scheduled)
            onSchedule?.let { OfferedAction(label, action.symbol, leaves = false, asks = true, run = it) }
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

/**
 * New mail [to] these people from the open message's account, or the list's; a list of all
 * accounts lets compose pick.
 */
private fun ActionSubject.newMail(
    button: BarButton,
    to: List<MailSender> = emptyList(),
): OfferedAction? =
    when {
        page != null -> plainOffer(button) { page.onCompose(ComposeRequest.New(page.message.location.accountId, to)) }
        list != null -> plainOffer(button) { list.onCompose(ComposeRequest.New(list.accountId, to)) }
        else -> null
    }

/** A draft's action, which leaves the compose page. */
private fun draftOffer(
    button: BarButton,
    run: () -> Unit,
    symbol: DrawableResource = button.symbol,
) = OfferedAction(button.buttonLabel(), symbol, leaves = true, run = run)
