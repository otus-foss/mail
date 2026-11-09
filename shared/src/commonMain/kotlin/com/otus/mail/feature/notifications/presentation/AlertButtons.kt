package com.otus.mail.feature.notifications.presentation

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.EmailByIdWriter
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.presentation.actions.buttonLabel
import com.otus.mail.core.messages.presentation.actions.offer
import com.otus.mail.core.settings.domain.NOTIFICATION_ACTION_LIMIT
import com.otus.mail.core.settings.domain.NotificationActionChoices

/**
 * The buttons an alert about mail of [account] shows: the chosen ones the account can run, in the
 * user's order, at most [NOTIFICATION_ACTION_LIMIT]. An action the account cannot run is left out
 * rather than shown as a button that fails, and no choice shows no buttons.
 */
fun List<MailAction>.alertButtons(account: AlertAccount): List<MailAction> =
    filter { it in NotificationActionChoices }
        .distinct()
        .filter(account::allows)
        .take(NOTIFICATION_ACTION_LIMIT)

/** Whether an alert's button for this action opens Mail, on a reply, instead of changing the message. */
val MailAction.opensMailFromAlert: Boolean
    get() = alertRun() == AlertRun.OpensReply

/**
 * Runs the button [action] of an alert on the email [emailId] of the login [loginId], with no list
 * row to act on; false when the message did not change.
 */
suspend fun EmailByIdWriter.runAlertButton(
    action: MailAction,
    loginId: String,
    emailId: String,
): Boolean = (action.alertRun() as? AlertRun.Change)?.change?.invoke(this, loginId, emailId) ?: false

/** What an alert that came back says in place of its account: which button's change failed. */
fun alertFailure(action: MailAction): String = "${action.buttonLabel()} didn't work"

private fun AlertAccount.allows(action: MailAction): Boolean =
    when (val run = action.alertRun()) {
        null -> false
        AlertRun.OpensReply -> canSend
        is AlertRun.Change -> run.needs == null || run.needs in mailboxRoles
    }

/**
 * How an alert's button runs: a [Change] edits the message by id, where the account has the
 * folder it [Change.needs], and Reply opens a draft. Read only marks the message read and Flag only
 * flags it, since an alert is about unread, unflagged mail.
 */
private sealed interface AlertRun {
    class Change(
        val needs: MailboxRole?,
        val change: suspend (EmailByIdWriter, String, String) -> Boolean,
    ) : AlertRun

    data object OpensReply : AlertRun
}

/** How an alert's button for this action runs; null for actions alerts never offer. */
private fun MailAction.alertRun(): AlertRun? =
    when (this) {
        MailAction.Trash -> AlertRun.Change(MailboxRole.Trash, EmailByIdWriter::trash)

        MailAction.Archive -> AlertRun.Change(MailboxRole.Archive, EmailByIdWriter::archive)

        MailAction.Spam -> AlertRun.Change(MailboxRole.Junk, EmailByIdWriter::reportSpam)

        MailAction.Read -> AlertRun.Change(needs = null, EmailByIdWriter::markRead)

        MailAction.Flag -> AlertRun.Change(needs = null, EmailByIdWriter::flag)

        MailAction.Reply -> AlertRun.OpensReply

        MailAction.ReadLater, MailAction.NotSpam, MailAction.Restore, MailAction.DeleteForever, MailAction.Move,
        MailAction.SuggestedMove, MailAction.RuleForSender, MailAction.ReplyAll, MailAction.Forward,
        MailAction.FollowUp, MailAction.NewMail, MailAction.Search, MailAction.Filters, MailAction.MarkAllRead,
        MailAction.EmptyFolder, MailAction.Send, MailAction.Attach, MailAction.Discard, MailAction.AllActions,
        MailAction.EditScheduled, MailAction.SendNow,
        -> null
    }
