package com.otus.mail.core.messages.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.generatedAvatar
import com.otus.avatar.ui.presentation.toChipAvatar
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.shared.people.components.NamedAvatar
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.theme.tokens.people.AvatarTokens
import com.otus.mail.core.messages.domain.MailSender

@Composable
internal fun SenderAvatar(
    sender: MailSender,
    size: Dp = AvatarTokens.size,
) {
    PersonAvatar(sender.chipAvatar, size)
}

/** The sender's avatar for a chip or an action bar token. */
internal val MailSender.chipAvatar: ChipAvatar
    get() = chipAvatar(avatar, name, address)

/** The avatar, named by address, for text that shows the address rather than the name. */
internal val MailSender.namedByAddress: NamedAvatar
    get() = NamedAvatar(address, chipAvatar)

internal fun chipAvatar(
    avatar: ResolvedAvatar?,
    name: String,
    address: String,
): ChipAvatar = (avatar ?: ResolvedAvatar(generatedAvatar(name, address))).toChipAvatar()

/** The first word of the name, or the address's local part when the name is blank. */
internal val MailSender.firstName: String
    get() =
        name
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull { it.isNotBlank() && '@' !in it }
            ?: address.substringBefore('@')
