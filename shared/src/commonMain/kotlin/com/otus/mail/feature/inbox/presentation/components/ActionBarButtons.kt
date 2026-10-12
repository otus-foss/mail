package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.DividerLabel
import com.otus.designsystem.components.ReorderableColumn
import com.otus.designsystem.components.ReorderableScope
import com.otus.designsystem.components.RowDensity
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_close
import com.otus.designsystem.theme.OtusColors
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.MailAction

/**
 * The bar's buttons in one draggable list, split by a main heading and a secondary heading. A
 * button dragged under the main heading becomes the main button. Removing the main button hands its
 * place to the first secondary one, so a bar with no secondary buttons cannot remove it.
 */
@Composable
internal fun ActionBarButtons(
    layout: ActionBarLayout,
    save: (ActionBarLayout) -> Unit,
) {
    SheetCaption("Drag them as you like.", heading = false)
    val rows =
        listOf(BarRow.Main, BarRow.Button(layout.primary), BarRow.Secondary) + layout.secondary.map(BarRow::Button)
    ReorderableColumn(
        rows,
        key = BarRow::key,
        onMove = { from, to ->
            val order = rows.filterIndexed { index, _ -> index != from }.let { it.take(to) + rows[from] + it.drop(to) }
            val split = order.indexOf(BarRow.Secondary)
            save(layout.regrouped(order.take(split).actions(), order.drop(split).actions()))
        },
        background = OtusColors.bgDark,
    ) { row ->
        when (row) {
            BarRow.Main -> {
                DividerLabel("Main button")
            }

            BarRow.Secondary -> {
                DividerLabel(
                    "Secondary buttons ${layout.secondary.size}/${ActionBarLayout.MAX_SECONDARY}",
                )
            }

            is BarRow.Button -> {
                ButtonRow(row.action, layout, save)
            }
        }
    }
}

@Composable
private fun ReorderableScope.ButtonRow(
    action: MailAction,
    layout: ActionBarLayout,
    save: (ActionBarLayout) -> Unit,
) {
    val slot = layout.secondary.indexOf(action)
    Row(verticalAlignment = Alignment.CenterVertically) {
        DragHandle(action.label)
        // The headings divide the list, so its rows drop their own hairlines.
        ActionRow(action, Modifier.weight(1f), RowDensity.Compact) {
            IconButton(
                "Remove ${action.label}",
                {
                    save(if (slot < 0) layout.regrouped(emptyList(), layout.secondary) else layout.withSlot(slot, null))
                },
                enabled = slot >= 0 || layout.secondary.isNotEmpty(),
            ) { Symbol(Res.drawable.symbol_close) }
        }
    }
}

/** A row of [ActionBarButtons]: one of the two headings, or a button. */
private sealed interface BarRow {
    val key: String

    data object Main : BarRow {
        override val key = "main"
    }

    data object Secondary : BarRow {
        override val key = "secondary"
    }

    data class Button(
        val action: MailAction,
    ) : BarRow {
        override val key = action.storageValue
    }
}

private fun List<BarRow>.actions(): List<MailAction> = filterIsInstance<BarRow.Button>().map { it.action }
