package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.otus.designsystem.mail.folders.components.FolderHint
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_event
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.resources.symbol_forward
import com.otus.designsystem.resources.symbol_reply
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.people.components.PersonChip
import com.otus.designsystem.shared.people.components.Recipient
import com.otus.designsystem.shared.rows.components.DetailLine
import com.otus.designsystem.shared.rows.components.DetailsTable
import com.otus.designsystem.theme.PanelTokens
import com.otus.designsystem.theme.ReadTokens
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MessageFolder
import org.jetbrains.compose.resources.DrawableResource

/**
 * The full record of a page under the sender pill: From, To, Cc, the [fullDate], every one of its
 * [folders] by full path, which open the folders sheet through [onFolders], and its marks. It never
 * says which of the user's addresses or accounts received it.
 */
@Composable
internal fun ReadSenderDetails(
    message: MailMessage,
    fullDate: String,
    folders: List<MessageFolder>,
    onFolders: () -> Unit,
    modifier: Modifier = Modifier,
    onPerson: ((MailSender) -> Unit)? = null,
) {
    val rows =
        buildList {
            add(DetailLine("From") { People(listOf(message.sender), onPerson) })
            if (message.to.isNotEmpty()) add(DetailLine("To") { People(message.to, onPerson) })
            if (message.cc.isNotEmpty()) add(DetailLine("Cc") { People(message.cc, onPerson) })
            add(DetailLine("Date") { Text(fullDate, style = panelTextStyle(), fontSize = ReadTokens.previewSize) })
            if (folders.isNotEmpty()) add(DetailLine("Folders") { FolderLines(folders, onFolders) })
            val marks = message.marks()
            if (marks.isNotEmpty()) add(DetailLine("Marks") { Marks(marks) })
        }
    DetailsTable(rows, modifier)
}

@Composable
private fun People(
    people: List<MailSender>,
    onPerson: ((MailSender) -> Unit)?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
        verticalArrangement = Arrangement.spacedBy(PanelTokens.iconGap),
    ) {
        people.forEach { person ->
            val name = person.name.ifBlank { person.address }
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
    Column(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Change folders", role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(PanelTokens.iconGap),
    ) {
        folders.forEach { FolderHint(listOf(folderName(it)), fontSize = ReadTokens.previewSize) }
    }
}

@Composable
private fun Marks(marks: List<Pair<DrawableResource, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ReadTokens.stripGap)) {
        marks.forEach { (icon, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(ReadTokens.glyphGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol(icon, size = ReadTokens.folderGlyphSize)
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
