package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole

/**
 * The buttons a new-mail alert can offer, in the order Settings lists them. Each runs without
 * opening Mail except Reply, which opens a reply to the message. Read only marks the message read
 * and Flag only flags it, since an alert is about unread, unflagged mail.
 */
val NotificationActionChoices: List<MailAction> =
    listOf(MailAction.Trash, MailAction.Archive, MailAction.Read, MailAction.Flag, MailAction.Spam, MailAction.Reply)

/** The buttons an alert offers until the user picks their own. */
val DefaultNotificationActions: List<MailAction> = listOf(MailAction.Trash, MailAction.Archive, MailAction.Read)

/** Android shows at most three buttons on a notification. */
const val NOTIFICATION_ACTION_LIMIT = 3

/** What one account lets an alert's buttons do: the roles of its mailboxes, and whether it can send mail. */
data class AlertAccount(
    val mailboxRoles: Set<MailboxRole>,
    val canSend: Boolean,
)

/** Whether a button running [action] works for mail of this account. */
fun AlertAccount.allows(action: MailAction): Boolean =
    when (action) {
        MailAction.Trash -> MailboxRole.Trash in mailboxRoles
        MailAction.Archive -> MailboxRole.Archive in mailboxRoles
        MailAction.Spam -> MailboxRole.Junk in mailboxRoles
        MailAction.Read, MailAction.Flag -> true
        MailAction.Reply -> canSend
        else -> false
    }

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

/** [actions] as one stored line, such as `trash,archive,read`; an empty line is no buttons. */
fun encodeNotificationActions(actions: List<MailAction>): String = actions.joinToString(",") { it.storageValue }

fun decodeNotificationActions(saved: String): List<MailAction> =
    decodeModules(saved, NotificationActionChoices, MailAction::storageValue, NOTIFICATION_ACTION_LIMIT)
