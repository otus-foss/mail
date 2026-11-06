package com.otus.mail.app

import com.otus.accounts.domain.ConnectedAccount
import com.otus.accounts.domain.ownAddresses
import com.otus.accounts.presentation.AccountAvatars
import com.otus.avatar.ui.presentation.OwnAccount
import com.otus.avatar.ui.presentation.OwnAvatarsState
import com.otus.avatar.ui.presentation.toChipAvatarOr
import com.otus.designsystem.shared.people.components.ChipAvatar

/** This account as the own-avatar editor knows it: its id, name, and own addresses. */
internal val ConnectedAccount.own: OwnAccount get() = OwnAccount(id, name, ownAddresses)

/** The avatars the account page shows, as [own] holds them; the generated ones until they load. */
internal class MailAccountAvatars(
    private val own: OwnAvatarsState,
) : AccountAvatars {
    override val error: String? get() = own.error

    override fun load(accounts: List<ConnectedAccount>) = own.load(accounts.map { it.own })

    override fun accountAvatar(account: ConnectedAccount): ChipAvatar =
        own.accountAvatar(account.own).toChipAvatarOr(account.name, account.username)

    override fun avatar(
        account: ConnectedAccount,
        email: String,
    ): ChipAvatar = own.avatar(account.own, email).toChipAvatarOr(account.name, email)
}
