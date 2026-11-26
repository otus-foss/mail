package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_check_circle
import com.otusfoss.core.designsystem.resources.symbol_flag
import com.otusfoss.core.designsystem.resources.symbol_mark_email_read
import com.otusfoss.core.designsystem.resources.symbol_mark_email_unread
import com.otusfoss.core.designsystem.shared.controls.elements.Chip
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.panels.components.ContextPanelScope
import com.otusfoss.mail.core.messages.domain.MailMessage
import org.jetbrains.compose.resources.DrawableResource

/** A pill in the selection panel that selects the shown rows it [matches]. */
enum class SelectionPick(
    val label: String,
    val icon: DrawableResource,
    val matches: (MailMessage) -> Boolean,
) {
    All("All", Res.drawable.symbol_check_circle, { true }),
    Unread("Unread", Res.drawable.symbol_mark_email_unread, { it.attributes.unread }),
    Read("Read", Res.drawable.symbol_mark_email_read, { !it.attributes.unread }),
    Flagged("Flagged", Res.drawable.symbol_flag, { it.attributes.flagged }),
}

/** A [pick] with rows to select, [on] while the selection is exactly those rows. */
class ShownPick(
    val pick: SelectionPick,
    val on: Boolean,
    val onClick: () -> Unit,
)

/** The picks with at least one of [shown] to select; a tap selects those rows in place of [selected]. */
internal fun selectionPicks(
    shown: List<MailMessage>,
    selected: List<MailMessage>,
    select: (List<MailMessage>) -> Unit,
): List<ShownPick> =
    SelectionPick.entries.mapNotNull { pick ->
        shown
            .filter(pick.matches)
            .takeIf { it.isNotEmpty() }
            ?.let { rows -> ShownPick(pick, rows.ids() == selected.ids()) { select(rows) } }
    }

private fun List<MailMessage>.ids(): Set<String> = map { it.id }.toSet()

/** The selection picks as the panel's pills. */
@Composable
internal fun ContextPanelScope.SelectionPickChips(picks: List<ShownPick>) {
    picks.forEach { shown ->
        item(shown.pick) {
            Chip(shown.pick.label, shown.onClick, selected = shown.on) { Symbol(shown.pick.icon) }
        }
    }
}
