package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.avatar.AvatarRequest
import com.otus.avatar.AvatarService
import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.avatarEmailKey
import com.otus.mail.core.avatar.MailAvatarOptions
import com.otus.mail.feature.folders.domain.AccountRules
import com.otus.mail.feature.folders.domain.sendersByFolder
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** A sender whose mail a rule moves into a folder. [avatar] is null until it resolves. */
data class FolderSender(
    val name: String,
    val address: String,
    val avatar: ResolvedAvatar? = null,
)

/**
 * Names and avatars for the senders that rules move into each folder. Rules store only addresses,
 * so [nameOf] looks for a name in mail already loaded, and the address stands in without one.
 * Avatars resolve like inbox senders' and share their cache.
 */
class FolderSenders(
    private val avatars: AvatarService?,
    private val nameOf: (address: String) -> String?,
) {
    private var resolved by mutableStateOf(emptyMap<Pair<String, String>, ResolvedAvatar>())

    fun of(
        accountId: String,
        addresses: List<String>,
    ): List<FolderSender> =
        addresses.map { address ->
            FolderSender(nameOf(address) ?: address, address, resolved[accountId to avatarEmailKey(address)])
        }

    /** Resolves the avatar of every sender in [accounts] that has none yet; failures keep initials. */
    suspend fun resolve(accounts: List<AccountRules>) {
        val service = avatars ?: return
        val missing =
            accounts
                .flatMap { account ->
                    account
                        .sendersByFolder()
                        .values
                        .flatten()
                        .map { account.accountId to it }
                }.distinctBy { (accountId, address) -> accountId to avatarEmailKey(address) }
                .filter { (accountId, address) -> (accountId to avatarEmailKey(address)) !in resolved }
        coroutineScope {
            missing.forEach { (accountId, address) ->
                launch {
                    val request = AvatarRequest(accountId, address, nameOf(address).orEmpty())
                    val avatar = service.resolve(request, MailAvatarOptions)
                    resolved = resolved + ((accountId to avatarEmailKey(address)) to avatar)
                }
            }
        }
    }
}
