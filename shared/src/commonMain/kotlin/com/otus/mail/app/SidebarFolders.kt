package com.otus.mail.app

import androidx.compose.runtime.Composable
import com.otus.designsystem.elements.SidebarCount
import com.otus.designsystem.elements.SidebarEntry
import com.otus.designsystem.elements.SidebarExpandButton
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.mail.feature.folders.domain.SharedFolder

/**
 * The user's folders, shared by every account, nested under their parents. A collapsed folder
 * hides its nested folders and counts their mail with its own.
 */
@Composable
internal fun SidebarFolderList(
    folders: List<SharedFolder>,
    collapsed: Set<List<String>>,
    onCollapse: (key: List<String>, collapsed: Boolean) -> Unit,
    current: MailPage,
    counter: SidebarCounter,
    onSelect: (MailPage) -> Unit,
    depth: Int = 0,
) {
    folders.forEach { folder ->
        val open = folder.key !in collapsed
        val page = MailPage.Shared(folder.key)
        SidebarEntry(
            folder.name,
            Res.drawable.symbol_folder,
            selected = current == page,
            onClick = { onSelect(page) },
            depth = depth,
            trailing = {
                counter
                    .of(
                        if (open) folder.folders else folder.flatten().flatMap { it.folders },
                    )?.let { SidebarCount(it) }
                if (folder.children.isNotEmpty()) {
                    SidebarExpandButton(folder.name, open, { onCollapse(folder.key, !it) })
                }
            },
        )
        if (open) SidebarFolderList(folder.children, collapsed, onCollapse, current, counter, onSelect, depth + 1)
    }
}
