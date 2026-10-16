package com.otus.mail.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.otus.designsystem.components.Collapsible
import com.otus.designsystem.elements.LocalIconTint
import com.otus.designsystem.elements.SidebarFolder
import com.otus.designsystem.elements.SidebarFolderEntry
import com.otus.designsystem.theme.FolderColor
import com.otus.mail.feature.folders.domain.SharedFolder

/**
 * The user's folders, shared by every account, nested under their parents. A folder with
 * subfolders folds them away when its icon is tapped and then counts their mail with its own.
 * [colorOf] names each folder's icon color, and a long press calls [onActions].
 */
internal class SidebarFolderTree(
    private val collapsed: Set<List<String>>,
    private val onCollapse: (key: List<String>, collapsed: Boolean) -> Unit,
    private val current: MailPage,
    private val counter: SidebarCounter,
    private val colorOf: (SharedFolder) -> String?,
    private val onSelect: (MailPage) -> Unit,
    private val onActions: (SharedFolder) -> Unit,
) {
    @Composable
    fun Rows(
        folders: List<SharedFolder>,
        depth: Int = 0,
    ) {
        folders.forEach { folder ->
            val open = folder.key !in collapsed
            val page = MailPage.Shared(folder.key)
            val count = counter.of(if (open) folder.folders else folder.flatten().flatMap { it.folders })
            CompositionLocalProvider(LocalIconTint provides FolderColor.named(colorOf(folder)).tint) {
                SidebarFolderEntry(
                    SidebarFolder(folder.name, depth, count),
                    selected = current == page,
                    expanded = open.takeIf { folder.children.isNotEmpty() },
                    onExpandedChange = { onCollapse(folder.key, !it) },
                    onClick = { onSelect(page) },
                    onLongClick = { onActions(folder) },
                )
            }
            if (folder.children.isNotEmpty()) {
                Collapsible(open) { Column { Rows(folder.children, depth + 1) } }
            }
        }
    }
}
