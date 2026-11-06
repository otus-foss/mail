package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.generatedAvatar
import com.otus.avatar.ui.presentation.toChipAvatar
import com.otus.designsystem.shared.people.components.AvatarStackChip
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.shared.people.components.NamedAvatar
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.shownName

@Composable
internal fun PeopleFilterChip(
    senders: List<MailSender>,
    onClick: () -> Unit,
) {
    val single = senders.singleOrNull()
    AvatarFilterChip(
        senders.map { it.chipAvatar },
        single?.let(::shortName),
        single?.let { "From ${it.shownName}" } ?: "From ${senders.size} people",
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

/**
 * One selection shows its avatar and [singleLabel]; several show overlapping avatars, and a count of
 * those that do not fit.
 */
@Composable
internal fun AvatarFilterChip(
    avatars: List<ChipAvatar>,
    singleLabel: String?,
    description: String,
    onClick: () -> Unit,
    detail: String? = null,
    prefix: String? = null,
) {
    val hidden = avatars.size - PanelTokens.CHIP_AVATAR_LIMIT
    AvatarStackChip(
        avatars,
        singleLabel ?: "+$hidden".takeIf { hidden > 0 },
        description,
        onClick,
        detail = detail,
        prefix = prefix,
    )
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

private fun shortName(sender: MailSender): String =
    sender.name
        .trim()
        .split(Regex("\\s+"))
        .firstOrNull { it.isNotBlank() && '@' !in it }
        ?: sender.address.substringBefore('@')
