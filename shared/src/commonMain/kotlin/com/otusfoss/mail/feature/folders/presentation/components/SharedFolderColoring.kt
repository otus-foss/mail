package com.otusfoss.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otusfoss.mail.core.mailbox.SharedFolder
import com.otusfoss.mail.feature.folders.presentation.FolderColorsState

/** The sheet that colors a long-pressed folder, as the sidebar offers. Only the user's own folders have colors. */
class SharedFolderColoring(
    private val colors: FolderColorsState,
) {
    private var coloring by mutableStateOf<SharedFolder?>(null)

    fun open(folder: SharedFolder) {
        if (folder.folders.any(colors::colorable)) coloring = folder
    }

    @Composable
    fun Sheet() {
        coloring?.let { folder ->
            FolderColorSheet(
                current = colors.colors.of(folder),
                onPick = { colors.setColor(folder.folders, it) },
                onDismiss = { coloring = null },
            )
        }
    }
}
