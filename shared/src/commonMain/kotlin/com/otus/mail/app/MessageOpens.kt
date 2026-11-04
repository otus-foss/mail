package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.listing
import com.otus.mail.feature.inbox.presentation.OpenRequest
import com.otus.mail.feature.notifications.domain.AlertedMail
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Opens the message a tapped notification names on the Inbox: the feed goes back to the page's
 * own mail and loads again, then [MailRequests.row] asks the Inbox to open the row that lists the
 * message. When the chosen scope hides the message's account, the pages show that account. A
 * message that left every inbox or was deleted meanwhile shows a toast instead.
 */
internal class MessageOpens(
    private val states: MailStates,
) {
    suspend fun open(mail: AlertedMail) {
        val feed = states.feed
        feed.show(null)
        feed.refresh()
        // The scope a cold start restores counts only once the accounts are known.
        snapshotFlow { feed.loading || states.accounts.loading }.first { !it }
        val row = feed.messages.listing(mail.loginId, mail.emailId)
        if (row == null) {
            states.moveToasts.toasts.show(ToastMessage("This message is no longer in your inbox"))
            return
        }
        if (!states.mailScope.shows(row)) states.chooseScope(MailScopeChoice.Account(mail.loginId))
        states.requests.row = OpenRequest(row.id) { states.requests.row = null }
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
        states.requests.message.filterNotNull().collect { mail ->
            show(MailPage.Inbox)
            opens.open(mail)
            states.requests.took(mail)
        }
    }
}
