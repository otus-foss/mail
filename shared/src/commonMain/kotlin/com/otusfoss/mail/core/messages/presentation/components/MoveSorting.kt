package com.otusfoss.mail.core.messages.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otusfoss.mail.core.messages.presentation.FutureSorting
import com.otusfoss.mail.core.messages.presentation.SortChoice

/** Whether a move also sorts future mail, and by which [SortChoice] of [future]. */
internal class MoveSorting(
    val future: FutureSorting?,
    val on: Boolean,
    val chosen: SortChoice?,
    val onToggle: (Boolean) -> Unit,
    val onChoose: (SortChoice) -> Unit,
) {
    /** The rule a move adds; null while the switch is off. */
    val rule: SortChoice?
        get() = chosen.takeIf { on }
}

/** Off by default. The choice is kept by label, so it survives while the choices are rebuilt. */
@Composable
internal fun rememberMoveSorting(future: FutureSorting?): MoveSorting {
    var on by rememberSaveable { mutableStateOf(false) }
    var label by rememberSaveable { mutableStateOf<String?>(null) }
    val choices = future?.choices.orEmpty()
    return MoveSorting(
        future,
        on = on && future != null,
        chosen = choices.firstOrNull { it.label == label } ?: choices.firstOrNull(),
        onToggle = { on = it },
        onChoose = { label = it.label },
    )
}
