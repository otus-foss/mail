package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.components.OptionsItem
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_block
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_delete_sweep
import com.otus.designsystem.resources.symbol_drive_file_move
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.resources.symbol_forward
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_info
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.designsystem.resources.symbol_notifications_off
import com.otus.designsystem.resources.symbol_person_add
import com.otus.designsystem.resources.symbol_print
import com.otus.designsystem.resources.symbol_reply
import com.otus.designsystem.resources.symbol_reply_all
import com.otus.designsystem.resources.symbol_report
import com.otus.designsystem.resources.symbol_verified_user
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.presentation.read.ReadMenuEntry
import com.otus.mail.feature.inbox.presentation.read.ReadMenuSection
import com.otus.mail.feature.inbox.presentation.read.detail
import com.otus.mail.feature.inbox.presentation.read.label
import com.otus.mail.feature.inbox.presentation.read.title
import org.jetbrains.compose.resources.DrawableResource

/**
 * The ⋯ menu's rows for [sections], each after a line and its caption, with [more], what the bar
 * offers beyond the menu, such as Rule for sender, at the end. Choosing a row runs [onEntry]. While
 * the page is [unread], its unread entry marks it read instead.
 */
internal fun readMenuItems(
    sections: List<ReadMenuSection>,
    onEntry: (ReadMenuEntry) -> Unit,
    more: List<OfferedAction>,
    onMore: (OfferedAction) -> Unit,
    unread: Boolean,
): List<OptionsItem> =
    buildList {
        sections.forEachIndexed { index, section ->
            if (index > 0) add(OptionsItem.Divider)
            section.part.title()?.let { add(OptionsItem.Caption(it)) }
            section.entries.forEach { add(it.item(onEntry, unread)) }
        }
        if (more.isNotEmpty()) add(OptionsItem.Divider)
        more.forEach { add(OptionsItem.Action(it.label, it.symbol, { onMore(it) })) }
    }

private fun ReadMenuEntry.item(
    onEntry: (ReadMenuEntry) -> Unit,
    unread: Boolean,
): OptionsItem =
    when {
        this is ReadMenuEntry.Mute -> {
            OptionsItem.Toggle(label(), icon, on, detail()) { onEntry(this) }
        }

        this == ReadMenuEntry.MarkUnread && unread -> {
            OptionsItem.Action("Mark as read", Res.drawable.symbol_mark_email_read, { onEntry(this) })
        }

        else -> {
            OptionsItem.Action(
                label(),
                icon,
                { onEntry(this) },
                opens = this is ReadMenuEntry.MoveTo,
                detail = detail(),
            )
        }
    }

private val ReadMenuEntry.icon: DrawableResource
    get() =
        when (this) {
            is ReadMenuEntry.OtherReply -> {
                if (action == MailAction.ReplyAll) Res.drawable.symbol_reply_all else Res.drawable.symbol_reply
            }

            ReadMenuEntry.Forward -> {
                Res.drawable.symbol_forward
            }

            ReadMenuEntry.MarkUnread -> {
                Res.drawable.symbol_mark_email_unread
            }

            is ReadMenuEntry.Flag -> {
                Res.drawable.symbol_flag
            }

            ReadMenuEntry.ViewDetails -> {
                Res.drawable.symbol_info
            }

            ReadMenuEntry.Print -> {
                Res.drawable.symbol_print
            }

            ReadMenuEntry.DeleteThisMessage -> {
                Res.drawable.symbol_delete
            }

            is ReadMenuEntry.AlwaysShowImages -> {
                Res.drawable.symbol_verified_user
            }

            ReadMenuEntry.BlockSender -> {
                Res.drawable.symbol_block
            }

            ReadMenuEntry.ReportSpam -> {
                Res.drawable.symbol_report
            }

            is ReadMenuEntry.MoveTo -> {
                Res.drawable.symbol_drive_file_move
            }

            is ReadMenuEntry.Mute -> {
                Res.drawable.symbol_notifications_off
            }

            ReadMenuEntry.SenderContact -> {
                Res.drawable.symbol_person_add
            }

            is ReadMenuEntry.People -> {
                Res.drawable.symbol_group
            }

            ReadMenuEntry.DeleteConversation -> {
                Res.drawable.symbol_delete_sweep
            }
        }
