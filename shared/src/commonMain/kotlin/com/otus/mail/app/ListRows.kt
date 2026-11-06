package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.accounts.domain.ownAddresses
import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.feature.inbox.presentation.RowLook

/** The Mail list settings and every account's sign-in and sending addresses and their avatars, for the rows. */
@Composable
internal fun rememberRowLook(states: MailStates): RowLook {
    val layout = states.behavior.behavior.list.layout
    val accounts = states.accounts.accounts
    LaunchedEffect(accounts) { states.ownAvatars.load(accounts.map { it.own }) }
    val avatars =
        accounts
            .flatMap { account ->
                account.ownAddresses.mapNotNull { email ->
                    states.ownAvatars.avatar(account.own, email)?.let { (account.id to EmailAddressKey(email)) to it }
                }
            }.toMap()
    return remember(layout, accounts, avatars) {
        RowLook(layout, OwnAddresses(accounts.associate { it.id to it.ownAddresses }), avatars)
    }
}
