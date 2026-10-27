package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.theme.AvatarTokens
import com.otus.mail.feature.inbox.domain.MailSender

@Composable
internal fun SenderAvatar(
    sender: MailSender,
    size: Dp = AvatarTokens.size,
) {
    PersonAvatar(sender.chipAvatar, size)
}
