package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.overlays.components.SelectionSheet
import com.otus.designsystem.shared.reorder.components.ReorderableColumn
import com.otus.designsystem.shared.rows.elements.nestIndent
import com.otus.designsystem.shared.selection.elements.SelectionRow
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.presentation.nestedByPath

/**
 * Chooses an ordered list. The chosen values come first, in order: drag a handle to move one, or tap
 * it to take it out. Each group of the other [choices] follows, with nested folders indented under
 * their parents, and tapping one adds it to the end. Values in [selected] without a choice, such as
 * a folder that failed to load, stay saved after the shown ones. [keyOf] gives a value's key.
 */
@Composable
internal fun <T> OrderedChoiceSheet(
    title: String,
    selected: List<T>,
    choices: List<OrderedChoice<T>>,
    keyOf: (T) -> String,
    onApply: (List<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(selected.map(keyOf)) }
    val shown = draft.mapNotNull { key -> choices.firstOrNull { it.key == key } }
    val values = selected.associateBy(keyOf) + choices.associate { it.key to it.value }
    SelectionSheet(
        title = title,
        onDismiss = onDismiss,
        onClear = { draft = emptyList() },
        onApply = {
            onApply(draft.mapNotNull(values::get))
            onDismiss()
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            SheetCaption("Shown")
            if (shown.isEmpty()) SheetCaption("Nothing shown yet. Add something below.", heading = false)
            ReorderableColumn(
                shown,
                key = { it.key },
                onMove = { from, to -> draft = draft.reordered(shown.map { it.key }, from, to) },
                spacing = SheetTokens.gap,
                background = OtusColors.bgDark,
            ) { choice ->
                SelectionRow(choice.path, selected = true, onClick = { draft = draft - choice.key }, leading = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DragHandle(choice.path)
                        Symbol(choice.icon)
                    }
                })
            }
            choices.filter { it.key !in draft }.groupBy { it.group }.forEach { (group, options) ->
                SheetCaption(group)
                options.nestedByPath { it.path }.forEach { (choice, name, depth) ->
                    SelectionRow(
                        name,
                        selected = false,
                        onClick = { draft = draft + choice.key },
                        modifier = Modifier.nestIndent(PageTokens.nestIndent, depth),
                        leading = { Symbol(choice.icon) },
                    )
                }
            }
        }
    }
}

/** The keys with the shown one at [from] moved to [to]; keys the sheet cannot show stay at the end. */
private fun List<String>.reordered(
    shown: List<String>,
    from: Int,
    to: Int,
): List<String> {
    val rest = shown.filterIndexed { index, _ -> index != from }
    return rest.take(to) + shown[from] + rest.drop(to) + (this - shown.toSet())
}
