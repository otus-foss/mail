package com.otus.mail.feature.read.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.otus.designsystem.shared.overlays.components.SheetPull
import com.otus.designsystem.shared.rows.components.Unfold
import com.otus.designsystem.shared.rows.components.rememberUnfold
import com.otus.mail.feature.read.presentation.ReadSheet
import com.otus.mail.feature.read.presentation.ReadState

/**
 * How the bottom strips open with a finger: the [thread] strip's message list, and the [folders]
 * drag, which opens the folders sheet with the [folderPull] the sheet then follows.
 */
internal class StripFolds(
    val thread: Unfold,
    val folders: FoldDrag,
    private val pull: MutableState<SheetPull?>,
) {
    val folderPull: SheetPull?
        get() = pull.value
}

/** The strips' folds for [state]'s Read view; a folders pull ends when its sheet closes. */
@Composable
internal fun rememberStripFolds(state: ReadState): StripFolds {
    val thread = rememberUnfold(state.threadOpen)
    val pull = remember { mutableStateOf<SheetPull?>(null) }
    LaunchedEffect(state.sheet) { if (state.sheet == null) pull.value = null }
    val folders =
        remember(state) {
            PullDrag { SheetPull().also { pull.value = it }.also { state.sheet = ReadSheet.Folders } }
        }
    return remember(thread, folders) { StripFolds(thread, folders, pull) }
}
