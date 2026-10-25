package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.accounts.presentation.toChipAvatar
import com.otus.designsystem.components.CompactContentRow
import com.otus.designsystem.components.PersonBadge
import com.otus.designsystem.components.RowSelection
import com.otus.designsystem.elements.BadgedAvatar
import com.otus.designsystem.theme.InboxTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.ConversationRowModel

/**
 * One conversation in a list, drawn from its prepared [model]. Read rows dim, also while the list is
 * selecting, so read and unread mail still look apart; only a selected row shows at full strength.
 * A selected row shows a check in place of the sender's avatar and its badge; a null [selection]
 * means the list is not selecting.
 */
@Composable
fun ConversationRow(
    message: MailMessage,
    model: ConversationRowModel,
    modifier: Modifier = Modifier,
    selection: RowSelection? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    CompactContentRow(
        text = model.text,
        dimmed = model.read && selection?.selected != true,
        onClick = onClick,
        modifier = modifier,
        selection = selection,
        onLongClick = onLongClick,
    ) {
        BadgedAvatar(model.badge?.let { badge -> { PersonBadge(badge.toChipAvatar()) } }) {
            SenderAvatar(message.sender, size = InboxTokens.compactAvatarSize)
        }
    }
}
