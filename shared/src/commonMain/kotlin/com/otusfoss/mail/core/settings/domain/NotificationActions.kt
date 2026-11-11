package com.otusfoss.mail.core.settings.domain

import com.otusfoss.mail.core.messages.domain.MailAction

/**
 * The buttons a new-mail alert can offer, in the order Settings lists them. Each runs without
 * opening Mail except Reply, which opens a reply to the message.
 */
val NotificationActionChoices: List<MailAction> =
    listOf(MailAction.Trash, MailAction.Archive, MailAction.Read, MailAction.Flag, MailAction.Spam, MailAction.Reply)

/** The buttons an alert offers until the user picks their own. */
val DefaultNotificationActions: List<MailAction> = listOf(MailAction.Trash, MailAction.Archive, MailAction.Read)

/** Android shows at most three buttons on a notification. */
const val NOTIFICATION_ACTION_LIMIT = 3

/** [actions] as one stored line, such as `trash,archive,read`; an empty line is no buttons. */
fun encodeNotificationActions(actions: List<MailAction>): String = actions.joinToString(",") { it.storageValue }

fun decodeNotificationActions(saved: String): List<MailAction> =
    decodeModules(saved, NotificationActionChoices, MailAction::storageValue, NOTIFICATION_ACTION_LIMIT)
