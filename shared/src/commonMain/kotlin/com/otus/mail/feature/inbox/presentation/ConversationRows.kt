package com.otus.mail.feature.inbox.presentation

import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.generatedAvatar
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.CompactRowText
import com.otus.designsystem.components.CountPlacement
import com.otus.designsystem.components.RowFolderIcon
import com.otus.designsystem.components.RowLine
import com.otus.designsystem.components.RowMark
import com.otus.designsystem.components.RowTextPart
import com.otus.designsystem.elements.FolderGlyphKind
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_reply
import com.otus.designsystem.theme.FolderColor
import com.otus.mail.feature.inbox.domain.FolderKind
import com.otus.mail.feature.inbox.domain.ListOrder
import com.otus.mail.feature.inbox.domain.MailListLayout
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.domain.PreviewLines
import com.otus.mail.feature.inbox.domain.RowFolder
import com.otus.mail.feature.inbox.domain.SenderLine
import com.otus.mail.feature.inbox.domain.receivedOn
import com.otus.mail.feature.inbox.domain.senderLine
import com.otus.mail.feature.inbox.domain.threadSubject
import kotlinx.datetime.LocalDate

/** How a list draws its rows: the user's Mail list settings and the user's own addresses. */
data class RowLook(
    val layout: MailListLayout = MailListLayout(),
    val own: OwnAddresses = OwnAddresses(emptyMap()),
    /** The avatar of each own address, by login id and lowercase address. */
    val ownAvatars: Map<Pair<String, String>, ResolvedAvatar> = emptyMap(),
)

/**
 * One conversation row, worked out before composition so scrolling only draws. [read] rows dim
 * unless they are selected. [badge] is the address the conversation came to, shown only
 * when the list mixes addresses.
 */
data class ConversationRowModel(
    val text: CompactRowText,
    val read: Boolean,
    val badge: ResolvedAvatar?,
)

/** Each of [messages]' rows by message id, with the badge only when they came to several of the user's addresses. */
fun conversationRows(
    messages: List<MailMessage>,
    look: RowLook,
    dateFormatter: DateFormatter,
    today: LocalDate,
): Map<String, ConversationRowModel> {
    val receivedOn = messages.associate { it.id to it.receivedOn(look.own) }
    val mixed =
        receivedOn.values
            .filterNotNull()
            .distinctBy(String::lowercase)
            .size > 1
    return messages.associate { message ->
        val address = receivedOn[message.id]?.takeIf { mixed }
        message.id to
            message.rowModel(
                look,
                address,
                dateFormatter.listDateLabel(message.lastReceived, today, look.layout.dateStyle),
            )
    }
}

private fun MailMessage.rowModel(
    look: RowLook,
    receivedOn: String?,
    date: String,
): ConversationRowModel {
    val layout = look.layout
    val people = senderLine(look.own)
    val subject = threadSubject(subject).ifBlank { NO_SUBJECT }
    val count = conversation?.messageCount?.takeIf { it >= 2 }
    val shownFolders = if (layout.folderIcons) labels.filterNot { it.readLater } else emptyList()
    val subjectLine = RowLine(listOf(RowTextPart(subject)))
    val peopleLine = people.toLine()
    val previewText = preview.ifBlank { body }
    val text =
        CompactRowText(
            first = if (layout.order == ListOrder.SenderFirst && layout.showSender) peopleLine else subjectLine,
            second =
                when {
                    !layout.showSender -> RowLine(listOf(RowTextPart(previewText)))
                    layout.order == ListOrder.SenderFirst -> subjectLine
                    else -> peopleLine
                },
            trailing = date,
            count = count?.toString(),
            countPlacement =
                when {
                    !layout.showSender -> CountPlacement.BeforeTrailing
                    layout.order == ListOrder.SenderFirst -> CountPlacement.AfterSecond
                    else -> CountPlacement.AfterFirst
                },
            marks = marks(people),
            folders = shownFolders.take(MAX_FOLDERS).map { it.icon() },
            moreFolders = (shownFolders.size - MAX_FOLDERS).coerceAtLeast(0),
            preview = previewText,
            previewLines = if (layout.showSender) layout.preview.lines else 0,
            description = description(subject, count, people, date, shownFolders, receivedOn),
            stateLabel = if (attributes.unread) "Unread" else "Read",
        )
    return ConversationRowModel(
        text,
        read = !attributes.unread,
        badge =
            receivedOn?.let {
                look.ownAvatars[location.accountId to it.lowercase()]
                    ?: ResolvedAvatar(generatedAvatar(location.account, it))
            },
    )
}

private fun MailMessage.marks(people: SenderLine): List<RowMark> =
    listOfNotNull(
        RowMark.Answered.takeIf { (conversation?.answered ?: attributes.answered) && people !is SenderLine.YouTo },
        RowMark.Attachment.takeIf { attributes.hasAttachment || attributes.attachments.isNotEmpty() },
        RowMark.Flagged.takeIf { attributes.flagged },
        RowMark.ReadLater.takeIf { labels.any { it.readLater } },
    )

private fun SenderLine.toLine(): RowLine =
    when (this) {
        is SenderLine.From -> {
            RowLine(listOf(RowTextPart(name)) + others.morePart())
        }

        is SenderLine.YouTo -> {
            RowLine(
                listOf(RowTextPart("You"), RowTextPart(" to", quiet = true), RowTextPart(" $name")) + others.morePart(),
                icon = Res.drawable.symbol_reply,
            )
        }
    }

private fun Int.morePart(): List<RowTextPart> =
    if (this >
        0
    ) {
        listOf(RowTextPart(" +$this", quiet = true))
    } else {
        emptyList()
    }

private fun RowFolder.icon(): RowFolderIcon =
    when (kind) {
        FolderKind.Inbox -> {
            RowFolderIcon(FolderGlyphKind.Inbox, null)
        }

        FolderKind.Folder -> {
            RowFolderIcon(
                FolderGlyphKind.Folder,
                FolderColor.named(color).takeIf {
                    it !=
                        FolderColor.Default
                },
            )
        }
    }

/** What TalkBack reads for the row, built from what it shows. */
private fun MailMessage.description(
    subject: String,
    count: Int?,
    people: SenderLine,
    date: String,
    folders: List<RowFolder>,
    receivedOn: String?,
): String =
    listOfNotNull(
        if (attributes.unread) "Unread" else "Read",
        subject + (count?.let { ", $it messages" } ?: ""),
        people.spoken(),
        date,
        "Answered".takeIf { (conversation?.answered ?: attributes.answered) && people !is SenderLine.YouTo },
        "Attachment".takeIf { attributes.hasAttachment || attributes.attachments.isNotEmpty() },
        "Flagged".takeIf { attributes.flagged },
        "Read later".takeIf { labels.any { it.readLater } },
        folders.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Folders: ") { it.name },
        receivedOn?.let { "Received on $it" },
    ).joinToString(". ", postfix = ".")

private fun SenderLine.spoken(): String =
    when (this) {
        is SenderLine.From -> "From $name${others.spokenOthers()}"
        is SenderLine.YouTo -> "You replied last to $name${others.spokenOthers()}"
    }

private fun Int.spokenOthers(): String =
    when {
        this <= 0 -> ""
        this == 1 -> " and 1 other"
        else -> " and $this others"
    }

private val PreviewLines.lines: Int
    get() =
        when (this) {
            PreviewLines.Off -> 0
            PreviewLines.One -> 1
            PreviewLines.Two -> 2
        }

/** The newest email of the conversation dates the row, so a reply the user just sent counts. */
private val MailMessage.lastReceived get() = conversation?.last?.received ?: received

private const val MAX_FOLDERS = 2
private const val NO_SUBJECT = "(No subject)"
