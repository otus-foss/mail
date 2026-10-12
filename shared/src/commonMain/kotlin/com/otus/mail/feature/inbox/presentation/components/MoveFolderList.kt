package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.Collapsible
import com.otus.designsystem.components.RowDensity
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.elements.SidebarExpandButton
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.presentation.MoveTarget
import com.otus.mail.feature.inbox.presentation.collapsedAway
import com.otus.mail.feature.inbox.presentation.parentIds

/**
 * The user's own folders under a caption, indented by nesting; nothing when none match. A folder
 * with nested folders collapses them with the chevron at the end of its row, while the row itself
 * still moves the message. Everything starts expanded, and a search shows every match.
 */
@Composable
internal fun MoveFolderList(
    folders: List<MoveTarget>,
    searching: Boolean,
    onMove: (MoveTarget) -> Unit,
) {
    var collapsed by rememberSaveable(stateSaver = IdsSaver) { mutableStateOf(emptySet<String>()) }
    if (folders.isEmpty()) return
    val parents = if (searching) emptySet() else folders.parentIds()
    val hidden = if (searching) emptySet() else folders.collapsedAway(collapsed)
    SheetCaption("Folders", RowTextInset)
    Column {
        folders.forEach { folder ->
            Collapsible(folder.folderId !in hidden) {
                SettingRow(
                    folder.name,
                    { onMove(folder) },
                    Modifier.padding(start = PageTokens.nestIndent * folder.depth),
                    icon = Res.drawable.symbol_folder,
                    density = RowDensity.Compact,
                    trailing = {
                        if (folder.folderId in parents) {
                            val open = folder.folderId !in collapsed
                            SidebarExpandButton(folder.name, open, { expand ->
                                collapsed = if (expand) collapsed - folder.folderId else collapsed + folder.folderId
                            })
                        }
                    },
                )
            }
        }
    }
}

private val IdsSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
