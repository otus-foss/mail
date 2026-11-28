package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otusfoss.core.avatar.ui.presentation.toChipAvatar
import com.otusfoss.core.designsystem.mail.inbox.components.CompactContentRow
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_check
import com.otusfoss.core.designsystem.resources.symbol_content_copy
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.people.components.PersonBadge
import com.otusfoss.core.designsystem.shared.people.elements.BadgedAvatar
import com.otusfoss.core.designsystem.shared.rows.components.EntryNeighbors
import com.otusfoss.core.designsystem.shared.rows.components.RowSelection
import com.otusfoss.mail.core.codes.presentation.components.rememberCodeCopy
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.presentation.components.SenderAvatar
import com.otusfoss.mail.feature.inbox.presentation.ConversationRowModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * One conversation in a list, drawn from its prepared [model]. A selected row shows a check in
 * place of the sender's avatar and its badge; a null [selection] means the list is not selecting.
 * [neighbors] rounds the card less where other rows touch it. A row with a one-time code ends its
 * second line with the code as a quick use; a tap copies it, says "Copied" for a moment, and runs
 * [onCodeCopied].
 */
@Composable
fun ConversationRow(
    message: MailMessage,
    model: ConversationRowModel,
    modifier: Modifier = Modifier,
    selection: RowSelection? = null,
    neighbors: EntryNeighbors = EntryNeighbors(),
    onLongClick: (() -> Unit)? = null,
    onCodeCopied: () -> Unit = {},
    onClick: () -> Unit,
) {
    val copyCode = rememberCodeCopy()
    var copied by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        delay(COPIED_SHOWN)
        copied = false
    }
    val quickUse =
        model.code?.let { code ->
            if (copied) {
                UiAction(COPIED, Res.drawable.symbol_check) {}
            } else {
                UiAction(code.shown, Res.drawable.symbol_content_copy) {
                    copyCode(code)
                    copied = true
                    onCodeCopied()
                }
            }
        }
    CompactContentRow(
        text = model.text.copy(quickUse = quickUse),
        read = model.read,
        onClick = onClick,
        modifier = modifier,
        selection = selection,
        neighbors = neighbors,
        size = model.size,
        onLongClick = onLongClick,
    ) {
        BadgedAvatar(model.badge?.let { badge -> { PersonBadge(badge.toChipAvatar()) } }) {
            SenderAvatar(message.sender, size = model.size.avatarSize)
        }
    }
}

/** How long the quick use says "Copied" before it shows the code again. */
private val COPIED_SHOWN = 2.seconds
private const val COPIED = "Copied"
