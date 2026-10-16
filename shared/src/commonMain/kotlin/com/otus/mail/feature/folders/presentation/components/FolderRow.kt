package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.avatar.generatedAvatar
import com.otus.designsystem.components.AvatarStack
import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.components.RowDensity
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.elements.LocalIconTint
import com.otus.designsystem.elements.SidebarExpandButton
import com.otus.designsystem.theme.FolderColor
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.mail.feature.folders.domain.FolderNode
import com.otus.mail.feature.folders.presentation.FolderSender
import com.otus.mail.feature.folders.presentation.folderIcon

/**
 * A folder as a compact row, indented by its nesting depth, as in the sidebar and the move sheet.
 * At its end, the [senders] whose mail rules move here, then a chevron when [expanded] is set.
 * [color] names the icon's color; null keeps the plain icon.
 */
@Composable
fun FolderRow(
    node: FolderNode,
    color: String?,
    senders: List<FolderSender>,
    expanded: Boolean?,
    onExpandedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val folder = node.folder
    CompositionLocalProvider(LocalIconTint provides FolderColor.named(color).tint) {
        FolderSettingRow(node, senders, expanded, onExpandedChange, onClick)
    }
}

@Composable
private fun FolderSettingRow(
    node: FolderNode,
    senders: List<FolderSender>,
    expanded: Boolean?,
    onExpandedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val folder = node.folder
    SettingRow(
        folder.name,
        onClick,
        Modifier.padding(start = PageTokens.nestIndent * node.depth),
        icon = folderIcon(node),
        density = RowDensity.Compact,
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PanelTokens.iconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (senders.isNotEmpty()) {
                    AvatarStack(
                        senders.map { it.chipAvatar },
                        label = senders.label(),
                        description = "Rules move mail from ${senders.joinToString { it.name }} here",
                    )
                }
                expanded?.let { SidebarExpandButton(folder.name, it, onExpandedChange) }
            }
        },
    )
}

/** The first sender's first name, and how many others there are; screen readers hear every name. */
private fun List<FolderSender>.label(): String = first().shortName + if (size > 1) " +${size - 1}" else ""

private val FolderSender.shortName: String
    get() =
        name.trim().split(Regex("\\s+")).firstOrNull { it.isNotBlank() && '@' !in it }
            ?: address.substringBefore('@')

private val FolderSender.chipAvatar: ChipAvatar
    get() {
        val fallback = avatar?.fallback ?: generatedAvatar(name, address)
        return ChipAvatar(fallback.initials, fallback.colorIndex, avatar?.image?.bytes)
    }
