package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.avatar.AvatarOptions
import com.otus.avatar.AvatarRequest
import com.otus.avatar.AvatarService
import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.avatarEmailKey
import com.otus.mail.feature.folders.domain.AccountFolderRules
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
 * Avatars resolve like inbox senders' with the current [avatarOptions] and share their cache.
 */
class FolderSenders(
    private val avatars: AvatarService?,
    private val avatarOptions: () -> AvatarOptions,
    private val nameOf: (address: String) -> String?,
) {
    // Kept by the options they were found with, so changing them never shows avatars found before.
    private var resolved by mutableStateOf(emptyMap<Triple<AvatarOptions, String, String>, ResolvedAvatar>())

    fun of(
        accountId: String,
        addresses: List<String>,
    ): List<FolderSender> {
        val options = avatarOptions()
        return addresses.map { address ->
            FolderSender(nameOf(address) ?: address, address, resolved[senderKey(options, accountId, address)])
        }
    }

    /** Resolves the avatar of every sender in [accounts] that has none yet; failures keep initials. */
    suspend fun resolve(accounts: List<AccountFolderRules>) {
        val service = avatars ?: return
        val options = avatarOptions()
        val missing =
            accounts
                .flatMap { account ->
                    val senders = account.sendersByFolder().values
                    senders.flatten().map { account.accountId to it }
                }.distinctBy { (accountId, address) -> accountId to avatarEmailKey(address) }
                .filter { (accountId, address) -> senderKey(options, accountId, address) !in resolved }
        coroutineScope {
            missing.forEach { (accountId, address) ->
                launch {
                    val request = AvatarRequest(accountId, address, nameOf(address).orEmpty())
                    val avatar = service.resolve(request, options)
                    resolved = resolved + (senderKey(options, accountId, address) to avatar)
                }
            }
        }
    }
}

private fun senderKey(
    options: AvatarOptions,
    accountId: String,
    address: String,
) = Triple(options, accountId, avatarEmailKey(address))
