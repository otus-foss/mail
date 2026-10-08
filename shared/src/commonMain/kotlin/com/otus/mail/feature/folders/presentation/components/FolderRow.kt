package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.otus.mail.designsystem.elements.Symbol
import com.otus.mail.designsystem.elements.controlPressFeedback
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PageTokens
import com.otus.mail.designsystem.theme.SheetTokens
import com.otus.mail.feature.folders.domain.FolderNode
import com.otus.mail.feature.folders.presentation.folderIcon

/** One folder in a tree: indented by depth, with its message counts. Tapping it offers its actions. */
@Composable
fun FolderRow(
    node: FolderNode,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(SheetTokens.rowRadius)
    val folder = node.folder
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SheetTokens.rowHeight)
            .clip(shape)
            .background(OtusColors.bgDark)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .controlPressFeedback(interactions, shape)
            .padding(SheetTokens.gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SheetTokens.gap),
    ) {
        Box(Modifier.padding(start = PageTokens.nestIndent * node.depth)) { Symbol(folderIcon(folder.role)) }
        Column(Modifier.weight(1f)) {
            Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                folderCounts(folder.total, folder.unread),
                fontSize = SheetTokens.detailSize,
                color = OtusColors.text2,
                maxLines = 1,
            )
        }
    }
}

private fun folderCounts(
    total: Long,
    unread: Long,
): String {
    val messages = if (total == 1L) "1 message" else "$total messages"
    return if (unread > 0) "$messages · $unread unread" else messages
}
