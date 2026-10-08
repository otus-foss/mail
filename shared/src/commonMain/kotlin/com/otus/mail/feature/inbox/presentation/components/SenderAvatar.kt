package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.avatar.generatedAvatar
import com.otus.designsystem.elements.Avatar
import com.otus.designsystem.elements.rememberAvatarImage
import com.otus.mail.feature.inbox.domain.MailSender

@Composable
internal fun SenderAvatar(sender: MailSender) {
    val fallback = sender.avatar?.fallback ?: generatedAvatar(sender.name, sender.address)
    val image = rememberAvatarImage(sender.avatar?.image?.bytes)
    Avatar(fallback.initials, image = image, colorIndex = fallback.colorIndex)
}
