package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.messages.domain.original
import com.otus.mail.core.messages.domain.withContent
import com.otus.mail.feature.inbox.presentation.LEFT_INBOX_NOTICE
import com.otus.mail.feature.inbox.presentation.OpenRequest
import com.otus.mail.feature.inbox.presentation.reloadedListing
import kotlinx.coroutines.flow.filterNotNull

/**
 * Opens the message a tapped notification names on the Inbox: once the Inbox's feed finds the row
 * that lists it, [MailRequests.row] asks the Inbox to open that row. When the chosen scope hides
 * the message's account, the pages show that account. A message that left every inbox or was
 * deleted meanwhile shows a toast instead. A reply request starts a reply to the message, with its
 * full body to quote, instead of opening it.
 */
internal class MessageOpens(
    private val states: MailStates,
) {
    suspend fun open(request: MessageRequest) {
        val mail = request.mail
        // The scope a cold start restores counts only once the accounts are known.
        val row = states.list.feed.reloadedListing(mail.loginId, mail.emailId) { states.accounts.loading }
        if (row == null) {
            states.moveToasts.toasts.show(ToastMessage(LEFT_INBOX_NOTICE))
            return
        }
        if (!states.mailScope.shows(row)) states.chooseScope(MailScopeChoice.Account(mail.loginId))
        if (request.reply) {
            val message = row.withContent(states.messageContent(row))
            states.requests.compose(ComposeRequest.Reply(message.original(), all = false))
        } else {
            states.requests.row = OpenRequest(row.id) { states.requests.row = null }
        }
    }
}

/**
 * Shows the Inbox with [show] for each message a notification asks to open, including one asked
 * for before this composition started, and opens it there. A request that a recreated activity
 * interrupts waits for the next composition.
 */
@Composable
internal fun MessageOpenEffects(
    states: MailStates,
    show: (MailPage) -> Unit,
) {
    val opens = remember(states) { MessageOpens(states) }
    LaunchedEffect(opens) {
        states.requests.message.filterNotNull().collect { request ->
            show(MailPage.Inbox)
            opens.open(request)
            states.requests.took(request)
        }
    }
}
