package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.mail.core.avatar.generatedAvatar
import com.otus.mail.designsystem.elements.Avatar
import com.otus.mail.designsystem.elements.decodeAvatarImage
import com.otus.mail.feature.inbox.domain.MailSender

@Composable
internal fun SenderAvatar(sender: MailSender) {
    val fallback = sender.avatar?.fallback ?: generatedAvatar(sender.name, sender.address)
    val bytes = sender.avatar?.image?.bytes
    val image = remember(bytes) { bytes?.let(::decodeAvatarImage) }
    Avatar(fallback.initials, image = image, colorIndex = fallback.colorIndex)
}
