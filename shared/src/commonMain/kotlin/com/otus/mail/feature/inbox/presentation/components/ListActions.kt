package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.presentation.MessageActions

/**
 * What the message list's bar can do with nothing selected. [results] are the messages on
 * screen, which Mark all read marks. New mail sends from [accountId] when the list shows one account.
 */
internal class ListActions(
    val results: List<MailMessage>,
    val accountId: String?,
    val messageActions: MessageActions?,
    val onSearch: () -> Unit,
    val onFilters: () -> Unit,
    val onCompose: (ComposeRequest) -> Unit,
)

/** [action] for the list; null for actions that need a message, and for Mark all read with nothing unread. */
internal fun ListActions.offer(action: MailAction): OfferedAction? =
    when (action) {
        MailAction.NewMail -> plainOffer(action) { onCompose(ComposeRequest.New(accountId)) }
        MailAction.Search -> plainOffer(action, onSearch)
        MailAction.Filters -> plainOffer(action, onFilters)
        MailAction.MarkAllRead -> offerMarkAllRead()
        else -> null
    }

private fun ListActions.offerMarkAllRead(): OfferedAction? {
    val actions = messageActions ?: return null
    val unread = results.filter { it.attributes.unread && actions.canMark(it, MessageFlag.Read) }
    return unread.takeIf { it.isNotEmpty() }?.let {
        plainOffer(MailAction.MarkAllRead) { unread.forEach { actions.onMark(it, MessageFlag.Read, true) } }
    }
}
