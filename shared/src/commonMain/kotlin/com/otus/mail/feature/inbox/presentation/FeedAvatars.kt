package com.otus.mail.feature.inbox.presentation

import com.otus.avatar.AvatarOptions
import com.otus.avatar.AvatarRequest
import com.otus.avatar.AvatarService
import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.avatarEmailKey
import com.otus.avatar.generatedAvatar
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The avatars of a feed's senders and accounts. Local images come back before a load shows, and
 * network photos follow without blocking mail or showing errors.
 */
internal class FeedAvatars(
    private val service: AvatarService?,
) {
    // The options the shown avatars were found with.
    private var shownOptions: AvatarOptions? = null

    /** Records that the shown rows' avatars were found with [options]. */
    fun shown(options: AvatarOptions) {
        shownOptions = options
    }

    /**
     * [incoming] with the local images of its senders, and the account avatars [shown] already has.
     * Avatars found with other [options], such as website icons since turned off, are not carried over.
     */
    suspend fun restored(
        incoming: List<MailMessage>,
        shown: List<MailMessage>,
        options: AvatarOptions,
    ): List<MailMessage> {
        val service = service ?: return incoming
        val kept = shown.takeIf { options == shownOptions }.orEmpty()
        val previous = kept.associate { it.sender.key() to it.sender.avatar }
        val accounts = kept.associate { it.location.accountId to it.location.accountAvatar }
        val restored = mutableMapOf<Pair<String, String>, ResolvedAvatar?>()
        return incoming.map { message ->
            val sender = message.sender
            val key = sender.key()
            if (key !in restored) {
                val cached = service.cached(AvatarRequest(sender.accountKey, sender.address), options)
                restored[key] = keepIdenticalImage(cached, previous[key])
            }
            val avatar = restored[key]?.copy(fallback = generatedAvatar(sender.name, sender.address))
            val location = message.location.copy(accountAvatar = accounts[message.location.accountId])
            message.copy(sender = sender.copy(avatar = avatar), location = location)
        }
    }

    /**
     * Resolves the senders and accounts of [messages] and applies each result with [edit] while
     * [isCurrent] says the load that asked is still the newest.
     */
    suspend fun resolve(
        messages: List<MailMessage>,
        options: AvatarOptions,
        isCurrent: () -> Boolean,
        edit: ((MailMessage) -> MailMessage) -> Unit,
    ) {
        val service = service ?: return
        coroutineScope {
            launch { resolveAccounts(service, messages, options, isCurrent, edit) }
            resolveSenders(service, messages, options, isCurrent, edit)
        }
    }

    /** Each account's own address resolves like a sender, for the inbox picker. */
    private suspend fun resolveAccounts(
        service: AvatarService,
        messages: List<MailMessage>,
        options: AvatarOptions,
        isCurrent: () -> Boolean,
        edit: ((MailMessage) -> MailMessage) -> Unit,
    ) {
        val accounts =
            messages
                .map { it.location }
                .distinctBy { it.accountId }
                .filter { it.accountAddress.isNotBlank() }
        coroutineScope {
            accounts.forEach { account ->
                launch {
                    val request = AvatarRequest(account.accountId, account.accountAddress, account.account)
                    val resolved = service.resolve(request, options)
                    if (!isCurrent()) return@launch
                    val avatar = keepIdenticalImage(resolved, account.accountAvatar) ?: resolved
                    edit { message ->
                        if (message.location.accountId == account.accountId) {
                            message.copy(location = message.location.copy(accountAvatar = avatar))
                        } else {
                            message
                        }
                    }
                }
            }
        }
    }

    private suspend fun resolveSenders(
        service: AvatarService,
        messages: List<MailMessage>,
        options: AvatarOptions,
        isCurrent: () -> Boolean,
        edit: ((MailMessage) -> MailMessage) -> Unit,
    ) {
        val senders = messages.map { it.sender }.distinctBy { it.key() }
        coroutineScope {
            senders.forEach { sender ->
                launch {
                    val resolved =
                        service.resolve(
                            AvatarRequest(sender.accountKey, sender.address, sender.name),
                            options,
                        )
                    if (!isCurrent()) return@launch
                    val avatar = keepIdenticalImage(resolved, sender.avatar) ?: resolved
                    edit { message ->
                        if (message.sender.key() == sender.key()) {
                            val updated =
                                avatar.copy(
                                    fallback = generatedAvatar(message.sender.name, message.sender.address),
                                )
                            message.copy(sender = message.sender.copy(avatar = updated))
                        } else {
                            message
                        }
                    }
                }
            }
        }
    }
}

private fun MailSender.key() = accountKey to avatarEmailKey(address)

/** Disk restoration may allocate new bytes. Keep a displayed image's identity when its content matches. */
private fun keepIdenticalImage(
    current: ResolvedAvatar?,
    previous: ResolvedAvatar?,
): ResolvedAvatar? {
    val image = previous?.image ?: return current
    return if (current?.image?.bytes?.contentEquals(image.bytes) == true) current.copy(image = image) else current
}
