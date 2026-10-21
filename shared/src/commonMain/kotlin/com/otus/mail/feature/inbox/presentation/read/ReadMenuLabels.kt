package com.otus.mail.feature.inbox.presentation.read

import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.presentation.components.FOLDER_PATH_SEPARATOR

/** The caption above a menu section: the sender's name, "Whole conversation", or none. */
fun ReadMenuPart.title(): String? =
    when (this) {
        ReadMenuPart.ThisMessage -> null
        is ReadMenuPart.Sender -> name
        ReadMenuPart.Conversation -> "Whole conversation"
    }

/** What a menu entry says. */
fun ReadMenuEntry.label(): String =
    when (this) {
        is ReadMenuEntry.OtherReply -> if (action == MailAction.ReplyAll) "Reply all" else "Reply to $name"
        is ReadMenuEntry.Flag -> if (flagged) "Unflag" else "Flag"
        is ReadMenuEntry.AlwaysShowImages -> "Always show images from $sender"
        is ReadMenuEntry.MoveTo -> "Move to…"
        is ReadMenuEntry.Mute -> "Mute conversation"
        else -> FIXED_LABELS.getValue(this)
    }

/** The smaller second line under a menu entry, when it has one. */
fun ReadMenuEntry.detail(): String? =
    when (this) {
        ReadMenuEntry.ViewDetails -> "Headers, encryption, original"
        ReadMenuEntry.DeleteThisMessage -> "Only this one, not the thread"
        is ReadMenuEntry.MoveTo -> suggestedPath?.let { "Usually ${it.joinToString(FOLDER_PATH_SEPARATOR)}" }
        else -> null
    }

private val FIXED_LABELS: Map<ReadMenuEntry, String> =
    mapOf(
        ReadMenuEntry.Forward to "Forward",
        ReadMenuEntry.MarkUnread to "Mark as unread",
        ReadMenuEntry.ViewDetails to "View details",
        ReadMenuEntry.Print to "Print or save as PDF",
        ReadMenuEntry.DeleteThisMessage to "Delete this message",
        ReadMenuEntry.BlockSender to "Block sender",
        ReadMenuEntry.ReportSpam to "Report spam or phishing",
        ReadMenuEntry.DeleteConversation to "Delete conversation",
    )
