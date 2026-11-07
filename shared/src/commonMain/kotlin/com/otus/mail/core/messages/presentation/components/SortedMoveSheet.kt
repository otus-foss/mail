package com.otus.mail.core.messages.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.shared.controls.components.ButtonPair
import com.otus.designsystem.shared.controls.elements.ActionButton
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.overlays.elements.sheetBodyInset
import com.otus.designsystem.shared.people.components.PersonText
import com.otus.designsystem.shared.rows.components.SummaryCard
import com.otus.designsystem.shared.rows.elements.Caption
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.core.messages.presentation.OlderMail
import com.otus.mail.core.messages.presentation.SortedMove

/**
 * Follows a move that added a rule: Undo puts everything back, and older inbox mail the rule
 * matches can move too. Leaving them, or dismissing the sheet, keeps the move and the rule.
 */
@Composable
internal fun SortedMoveSheet(
    move: SortedMove,
    onDismiss: () -> Unit,
) {
    var checking by remember(move) { mutableStateOf(true) }
    val older by produceState<OlderMail?>(null, move) {
        value = move.older()
        checking = false
    }
    val count = older?.count ?: 0
    SheetFrame(
        "Moved to ${move.folderName}",
        onDismiss,
        done =
            UiAction("Undo", onClick = {
                move.undo()
                onDismiss()
            }),
    ) {
        Column(
            Modifier.sheetBodyInset(),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            PersonText("New mail ${move.catches} will go there too.", move.person?.namedByAddress, RowTextInset)
            when {
                checking -> {
                    OlderMailCard("Checking Inbox and Archive…", "Older mail ${move.catches}")
                }

                older == null -> {
                    Caption(
                        "Couldn't check Inbox and Archive for older mail.",
                        RowTextInset,
                        heading = false,
                    )
                }

                count == 0 -> {
                    Caption("No older mail in Inbox or Archive matches.", RowTextInset, heading = false)
                }

                else -> {
                    OlderMailCard(olderTitle(count), "All ${move.catches}, still in Inbox or Archive")
                }
            }
            if (checking || count > 0) {
                val moveOlder = {
                    older?.move?.invoke()
                    onDismiss()
                }
                ButtonPair(
                    UiAction("Leave them", onClick = onDismiss),
                    UiAction(
                        if (checking) "Move them too" else "Move $count too",
                        enabled = !checking,
                        onClick = moveOlder,
                    ),
                )
            } else {
                ActionButton("Done", onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun OlderMailCard(
    title: String,
    subtitle: String,
) {
    SummaryCard(title, subtitle, leading = { Symbol(Res.drawable.symbol_inbox) })
}

private fun olderTitle(count: Int): String =
    if (count == 1) "1 older message matches" else "$count older messages match"
