package com.otus.mail.feature.inbox.presentation.actions

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_schedule_send
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.original
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MessageMarks
import com.otus.mail.feature.inbox.presentation.MoveTarget
import org.jetbrains.compose.resources.DrawableResource

/**
 * What the actions of one place work on. [messages] are the rows a swipe, the selection, or the
 * Read view acts on; empty on a list with nothing selected. [marks] set read and flag marks, the
 * list's own unless a place marks something else, such as one page of a conversation. [page] is
 * the open message replies answer, in a folder of [role]. [list] and [draft] are the list on screen
 * and the draft being written, for the places that show them. Drafts start through [onCompose].
 */
internal class ActionSubject(
    val actions: MessageActions?,
    val messages: List<MailMessage> = emptyList(),
    val marks: MessageMarks? = actions?.marks,
    val chooseFolder: ChooseFolder = { _, _ -> },
    val suggestion: MoveTarget? = null,
    val page: MailMessage? = null,
    val role: MailboxRole? = null,
    val list: ListSubject? = null,
    val draft: DraftSubject? = null,
    val onCompose: ((ComposeRequest) -> Unit)? = null,
)

/**
 * The message list with nothing selected: Mark all read marks the unread [results] on screen, and
 * New mail sends from [accountId] when the list shows one account.
 */
internal class ListSubject(
    val results: List<MailMessage>,
    val accountId: String?,
    val onSearch: () -> Unit,
    val onFilters: () -> Unit,
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
 * because folders belong to an account. Suggested move goes to [ActionSubject.suggestion], when
 * there is one. All actions is the bar's own menu, so it is never offered.
 */
internal fun ActionSubject.offer(button: BarButton): OfferedAction? =
    when (button) {
        is BarButton.MoveTo -> actions?.offerMoveTo(messages, button)
        MailAction.Read -> marks?.offer(messages, MessageFlag.Read)
        MailAction.Flag -> marks?.offer(messages, MessageFlag.Flagged)
        MailAction.ReadLater -> actions?.offerReadLater(messages)
        MailAction.Archive -> actions?.offerFiling(messages, MailboxRole.Archive, button)
        MailAction.Trash -> actions?.offerFiling(messages, MailboxRole.Trash, button)
        MailAction.Spam -> actions?.offerFiling(messages, MailboxRole.Junk, button)
        MailAction.NotSpam -> actions?.offerNotSpam(messages)
        MailAction.Restore -> actions?.offerRestore(messages)
        MailAction.DeleteForever -> actions?.offerDeleteForever(messages)
        MailAction.Move -> actions?.offerMove(messages, chooseFolder)
        MailAction.SuggestedMove -> suggestion?.let { actions?.offerFolder(messages, it) }
        MailAction.RuleForSender -> actions?.offerRule(messages)
        MailAction.Reply -> page?.let { compose(button, ComposeRequest.Reply(it.original(), all = false)) }
        MailAction.ReplyAll -> page?.let { compose(button, ComposeRequest.Reply(it.original(), all = true)) }
        MailAction.Forward -> page?.let { compose(button, ComposeRequest.Forward(it.original())) }
        MailAction.FollowUp -> page?.takeIf { role == MailboxRole.Sent }?.let { compose(button, followUp(it)) }
        MailAction.NewMail -> newMail(button)
        MailAction.Search -> list?.let { plainOffer(button, it.onSearch) }
        MailAction.Filters -> list?.let { plainOffer(button, it.onFilters) }
        MailAction.MarkAllRead -> list?.let(::offerMarkAllRead)
        MailAction.EmptyFolder -> list?.let { actions?.offerEmpty() }
        MailAction.Send -> draft?.onSend?.let { draftOffer(button, it, sendSymbol(draft.scheduled)) }
        MailAction.Attach -> draft?.onAttach?.let { draftOffer(button, it) }
        MailAction.Discard -> draft?.let { draftOffer(button, it.onDiscard) }
        MailAction.AllActions -> null
    }

private fun followUp(page: MailMessage) = ComposeRequest.FollowUp(page.original())

private fun ActionSubject.compose(
    button: BarButton,
    request: ComposeRequest,
): OfferedAction? = onCompose?.let { plainOffer(button) { it(request) } }

/** New mail from the open message's account, or the list's; a list of all accounts lets compose pick. */
private fun ActionSubject.newMail(button: BarButton): OfferedAction? =
    when {
        page != null -> compose(button, ComposeRequest.New(page.location.accountId))
        list != null -> compose(button, ComposeRequest.New(list.accountId))
        else -> null
    }

private fun ActionSubject.offerMarkAllRead(list: ListSubject): OfferedAction? {
    val marks = marks ?: return null
    val unread = list.results.filter { it.attributes.unread && marks.can(it, MessageFlag.Read) }
    return unread.takeIf { it.isNotEmpty() }?.let {
        plainOffer(MailAction.MarkAllRead) { marks.set(unread, MessageFlag.Read, true) }
    }
}

private fun sendSymbol(scheduled: Boolean) =
    if (scheduled) Res.drawable.symbol_schedule_send else MailAction.Send.symbol

/** A draft's action, which leaves the compose page. */
private fun draftOffer(
    button: BarButton,
    run: () -> Unit,
    symbol: DrawableResource = button.symbol,
) = OfferedAction(button.buttonLabel(), symbol, leaves = true, run = run)
