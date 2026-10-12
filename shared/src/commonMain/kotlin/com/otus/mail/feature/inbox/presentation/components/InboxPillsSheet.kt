package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ReorderableColumn
import com.otus.designsystem.components.SelectionSheet
import com.otus.designsystem.elements.SelectionRow
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.nestedByPath
import org.jetbrains.compose.resources.DrawableResource

/**
 * Chooses the inbox row's pills. The shown pills come first, in row order: drag a handle to move
 * one, or tap it to take it off the row. The filters and each group of folder pills follow, with
 * nested folders indented under their parents, and tapping one adds it to the end of the row.
 * Pills without a choice here, such as a folder that failed to load, stay saved after the shown
 * ones.
 */
@Composable
internal fun InboxPillsSheet(
    selected: List<InboxPill>,
    folders: List<FolderPillOption>,
    onApply: (List<InboxPill>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = PillsSaver) { mutableStateOf(selected) }
    val choices = pillChoices(folders)
    val shown = draft.mapNotNull { pill -> choices.firstOrNull { it.pill == pill } }
    SelectionSheet(
        title = "Pills",
        onDismiss = onDismiss,
        onClear = { draft = emptyList() },
        onApply = {
            onApply(draft)
            onDismiss()
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            SheetCaption("Shown")
            if (shown.isEmpty()) SheetCaption("No pills yet. Add one below.", heading = false)
            ReorderableColumn(
                shown,
                key = { it.pill.storageValue },
                onMove = { from, to -> draft = draft.reordered(shown, from, to) },
                spacing = SheetTokens.gap,
                background = OtusColors.bgDark,
            ) { choice ->
                SelectionRow(choice.title, selected = true, onClick = { draft = draft - choice.pill }, leading = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DragHandle(choice.title)
                        Symbol(choice.icon)
                    }
                })
            }
            choices.filter { it.pill !in draft }.groupBy { it.group }.forEach { (group, options) ->
                SheetCaption(group)
                options.nestedByPath { it.title }.forEach { (choice, title, depth) ->
                    SelectionRow(
                        title,
                        selected = false,
                        onClick = { draft = draft + choice.pill },
                        modifier = Modifier.padding(start = PageTokens.nestIndent * depth),
                        leading = { Symbol(choice.icon) },
                    )
                }
            }
        }
    }
}

/** A pill as the sheet lists it, under [group]. */
private data class PillChoice(
    val pill: InboxPill,
    val title: String,
    val icon: DrawableResource,
    val group: String,
)

private fun pillChoices(folders: List<FolderPillOption>): List<PillChoice> =
    InboxPill.Filter.entries.map { PillChoice(it, it.label, it.icon, "Filters") } +
        folders.map { PillChoice(it.pill, it.path, it.icon, it.group) }

/** The row with the shown pill at [from] moved to [to]; pills the sheet cannot show stay at the end. */
private fun List<InboxPill>.reordered(
    shown: List<PillChoice>,
    from: Int,
    to: Int,
): List<InboxPill> {
    val order = shown.map { it.pill }
    val rest = order.filterIndexed { index, _ -> index != from }
    return rest.take(to) + order[from] + rest.drop(to) + (this - order.toSet())
}

private val PillsSaver =
    listSaver<List<InboxPill>, String>(
        save = { pills -> pills.map { it.storageValue } },
        restore = { saved -> saved.mapNotNull(InboxPill::fromStorageValue) },
    )
