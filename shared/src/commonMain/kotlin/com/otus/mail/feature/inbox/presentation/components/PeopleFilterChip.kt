package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.otus.avatar.generatedAvatar
import com.otus.designsystem.elements.Avatar
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.elements.rememberAvatarImage
import com.otus.designsystem.theme.OtusColors
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
    Chip(
        label,
        onClick,
        Modifier.semantics { contentDescription = description },
        selected = true,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(-PanelTokens.chipAvatarOverlap)) {
            shown.forEach { ChipAvatar(it) }
        }
    }
}

@Composable
private fun ChipAvatar(sender: MailSender) {
    val fallback = sender.avatar?.fallback ?: generatedAvatar(sender.name, sender.address)
    Avatar(
        fallback.initials,
        image = rememberAvatarImage(sender.avatar?.image?.bytes),
        colorIndex = fallback.colorIndex,
        size = PanelTokens.iconSize,
        ring = BorderStroke(PanelTokens.borderWidth, OtusColors.accentLight),
    )
}

private fun shortName(sender: MailSender): String =
    sender.name
        .trim()
        .split(Regex("\\s+"))
        .firstOrNull { it.isNotBlank() && '@' !in it }
        ?: sender.address.substringBefore('@')
