package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.otusfoss.core.designsystem.mail.folders.components.FolderHint
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_event
import com.otusfoss.core.designsystem.resources.symbol_flag
import com.otusfoss.core.designsystem.resources.symbol_forward
import com.otusfoss.core.designsystem.resources.symbol_reply
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.people.components.PersonChip
import com.otusfoss.core.designsystem.shared.people.components.Recipient
import com.otusfoss.core.designsystem.shared.rows.components.DetailLine
import com.otusfoss.core.designsystem.theme.tokens.content.ReadTokens
import com.otusfoss.core.designsystem.theme.tokens.core.Spacing
import com.otusfoss.core.designsystem.theme.tokens.rows.FolderTokens
import com.otusfoss.core.designsystem.theme.tokens.rows.StripTokens
import com.otusfoss.core.designsystem.theme.typography.panelTextStyle
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.MessageFolder
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import org.jetbrains.compose.resources.DrawableResource

/**
 * The full record of a message: From, To, Cc, Bcc, a Reply-to that differs from the sender, the
 * [fullDate], every one of its [folders] by full path, side by side while they fit, which open the
 * folders sheet through [onFolders], and its marks. It never says which of the user's addresses or
 * accounts received it.
 */
internal fun messageDetailLines(
    message: MailMessage,
    fullDate: String,
    folders: List<MessageFolder>,
    onFolders: () -> Unit,
    onPerson: ((MailSender) -> Unit)?,
): List<DetailLine> =
    buildList {
        val people = { label: String, list: List<MailSender> ->
            if (list.isNotEmpty()) add(DetailLine(label) { People(list, onPerson) })
        }
        people("From", listOf(message.sender))
        people("To", message.to)
        people("Cc", message.cc)
        people("Bcc", message.bcc)
        people("Reply-to", message.replyTo.filterNot { it.address.equals(message.sender.address, ignoreCase = true) })
        add(DetailLine("Date") { Text(fullDate, style = panelTextStyle(), fontSize = ReadTokens.previewSize) })
        if (folders.isNotEmpty()) add(DetailLine("Folders") { FolderLines(folders, onFolders) })
        val marks = message.marks()
        if (marks.isNotEmpty()) add(DetailLine("Marks") { Marks(marks) })
    }

@Composable
private fun People(
    people: List<MailSender>,
    onPerson: ((MailSender) -> Unit)?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.inlineGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.iconGap),
    ) {
        people.forEach { person ->
            val name = person.shownName
            PersonChip(
                Recipient(name, person.chipAvatar, "$name, ${person.address}"),
                onClick = onPerson?.let { { it(person) } },
            )
        }
    }
}

@Composable
private fun FolderLines(
    folders: List<MessageFolder>,
    onClick: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Change folders", role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(StripTokens.gap),
        verticalArrangement = Arrangement.spacedBy(Spacing.iconGap),
    ) {
        folders.forEach { FolderHint(listOf(folderName(it)), fontSize = ReadTokens.previewSize) }
    }
}

@Composable
private fun Marks(marks: List<Pair<DrawableResource, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(StripTokens.gap)) {
        marks.forEach { (icon, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(StripTokens.glyphGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol(icon, size = FolderTokens.glyphSize)
                Text(label, style = panelTextStyle(), fontSize = ReadTokens.previewSize)
            }
        }
    }
}

/** The marks the inbox shows on a row, as a symbol and a word each. */
private fun MailMessage.marks(): List<Pair<DrawableResource, String>> =
    listOfNotNull(
        (Res.drawable.symbol_flag to "Flagged").takeIf { attributes.flagged },
        (Res.drawable.symbol_reply to "Answered").takeIf { attributes.answered },
        (Res.drawable.symbol_forward to "Forwarded").takeIf { attributes.forwarded },
        (Res.drawable.symbol_event to "Invitation").takeIf { attributes.invite },
    )
