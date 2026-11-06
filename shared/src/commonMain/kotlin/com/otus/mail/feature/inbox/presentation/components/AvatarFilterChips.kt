package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.people.components.AvatarStackChip
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.theme.tokens.controls.ControlTokens
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.shownName
import com.otus.mail.core.messages.presentation.components.chipAvatar
import com.otus.mail.core.messages.presentation.label
import com.otus.mail.core.settings.presentation.label

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
    val hidden = avatars.size - ControlTokens.CHIP_AVATAR_LIMIT
    AvatarStackChip(
        avatars,
        singleLabel ?: "+$hidden".takeIf { hidden > 0 },
        description,
        onClick,
        detail = detail,
        prefix = prefix,
    )
}

private fun shortName(sender: MailSender): String =
    sender.name
        .trim()
        .split(Regex("\\s+"))
        .firstOrNull { it.isNotBlank() && '@' !in it }
        ?: sender.address.substringBefore('@')
