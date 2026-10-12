package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otus.designsystem.components.ChoiceDialog
import com.otus.mail.feature.inbox.presentation.MoveTarget

/**
 * The "Move to" choice of an action bar or menu. [choose] opens it; picking a folder runs
 * [onChosen] first, such as closing the page the messages leave, and then moves them.
 */
internal class FolderChoice(
    private val onChosen: () -> Unit,
) {
    private var request by mutableStateOf<Pair<List<MoveTarget>, (MoveTarget) -> Unit>?>(null)

    fun choose(
        targets: List<MoveTarget>,
        move: (MoveTarget) -> Unit,
    ) {
        request = targets to move
    }

    @Composable
    fun Dialog() {
        val (targets, move) = request ?: return
        ChoiceDialog<MoveTarget?>(
            "Move to",
            targets,
            selected = null,
            label = { it?.label.orEmpty() },
            onSelect = { target ->
                target?.let {
                    onChosen()
                    move(it)
                }
            },
            onDismiss = { request = null },
        )
    }
}

@Composable
internal fun rememberFolderChoice(onChosen: () -> Unit): FolderChoice = remember { FolderChoice(onChosen) }
