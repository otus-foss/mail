package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.avatar.generatedAvatar
import com.otus.designsystem.components.AvatarStackChip
import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.theme.PanelTokens
import com.otus.mail.feature.inbox.domain.MailSender

/** One selected sender shows avatar and first name; several show overlapping avatars only. */
@Composable
internal fun PeopleFilterChip(
    senders: List<MailSender>,
    onClick: () -> Unit,
) {
    val single = senders.singleOrNull()
    val shown = senders.take(PanelTokens.CHIP_AVATAR_LIMIT)
    val hidden = senders.size - shown.size
    val label =
        when {
            single != null -> shortName(single)
            hidden > 0 -> "+$hidden"
            else -> null
        }
    val description =
        single?.let { "From ${it.name.ifBlank { it.address }}" } ?: "From ${senders.size} people"
    AvatarStackChip(
        avatars =
            shown.map { sender ->
                val fallback = sender.avatar?.fallback ?: generatedAvatar(sender.name, sender.address)
                ChipAvatar(fallback.initials, fallback.colorIndex, sender.avatar?.image?.bytes)
            },
        label = label,
        description = description,
        onClick = onClick,
    )
}

private fun shortName(sender: MailSender): String =
    sender.name
        .trim()
        .split(Regex("\\s+"))
        .firstOrNull { it.isNotBlank() && '@' !in it }
        ?: sender.address.substringBefore('@')
