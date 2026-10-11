package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.generatedAvatar
import com.otus.designsystem.components.AvatarStackChip
import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.theme.PanelTokens
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailSender

@Composable
internal fun PeopleFilterChip(
    senders: List<MailSender>,
    onClick: () -> Unit,
) {
    val single = senders.singleOrNull()
    AvatarFilterChip(
        senders.map { chipAvatar(it.avatar, it.name, it.address) },
        single?.let(::shortName),
        single?.let { "From ${it.name.ifBlank { it.address }}" } ?: "From ${senders.size} people",
        onClick,
    )
}

/** The accounts, then the searched [mailboxes] after a divider line. */
@Composable
internal fun AccountsFilterChip(
    accounts: List<MailLocation>,
    mailboxes: MailboxSummary?,
    onClick: () -> Unit,
) {
    val single = accounts.singleOrNull()
    val accountsDescription = single?.let { "${it.account} account" } ?: "${accounts.size} accounts"
    AvatarFilterChip(
        accounts.map { chipAvatar(it.accountAvatar, it.account, it.accountAddress) },
        // Beside particular inboxes, one account's avatar is enough to recognize it.
        single?.account?.takeUnless { mailboxes?.specific == true },
        listOfNotNull(accountsDescription, mailboxes?.description).joinToString(", "),
        onClick,
        detail = mailboxes?.label,
    )
}

/** One selection shows its avatar and [singleLabel]; several show overlapping avatars only. */
@Composable
private fun AvatarFilterChip(
    avatars: List<ChipAvatar>,
    singleLabel: String?,
    description: String,
    onClick: () -> Unit,
    detail: String? = null,
) {
    val hidden = avatars.size - PanelTokens.CHIP_AVATAR_LIMIT
    AvatarStackChip(avatars, singleLabel ?: "+$hidden".takeIf { hidden > 0 }, description, onClick, detail = detail)
}

private fun chipAvatar(
    avatar: ResolvedAvatar?,
    name: String,
    address: String,
): ChipAvatar {
    val fallback = avatar?.fallback ?: generatedAvatar(name, address)
    return ChipAvatar(fallback.initials, fallback.colorIndex, avatar?.image?.bytes)
}

private fun shortName(sender: MailSender): String =
    sender.name
        .trim()
        .split(Regex("\\s+"))
        .firstOrNull { it.isNotBlank() && '@' !in it }
        ?: sender.address.substringBefore('@')
