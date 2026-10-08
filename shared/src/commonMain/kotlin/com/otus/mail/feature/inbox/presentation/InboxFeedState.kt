package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.avatar.AvatarOptions
import com.otus.avatar.AvatarRequest
import com.otus.avatar.AvatarService
import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.avatarEmailKey
import com.otus.avatar.generatedAvatar
import com.otus.mail.feature.inbox.domain.InboxFailure
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Loaded messages of the unified inbox or one folder. A refresh replaces any refresh still in flight. */
class InboxFeedState(
    private val source: MailSource,
    private val scope: CoroutineScope,
    private val avatars: AvatarService? = null,
    private val avatarOptions: AvatarOptions =
        AvatarOptions(
            externalContactImages = true,
            domainIcons = true,
            domainIconProxy = true,
        ),
    private val mailScope: MailScope = MailScope.Inbox,
) {
    var messages by mutableStateOf(emptyList<MailMessage>())
        private set
    var loading by mutableStateOf(false)
        private set

    /** Shown when some accounts could not be loaded; messages from the others remain. */
    var notice by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    /** Shows [text] in place of the last load's notice, such as a failed change. */
    fun report(text: String) {
        notice = text
    }

    fun refresh() {
        job?.cancel()
        job =
            scope.launch {
                loading = true
                try {
                    val snapshot = source.messages(mailScope)
                    messages = withCachedAvatars(snapshot.messages)
                    notice = snapshot.unavailable.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.notice() }
                } catch (expected: InboxStorageException) {
                    notice = "Couldn't load saved accounts. Try again in Settings."
                } finally {
                    loading = false
                }
                coroutineScope {
                    launch { resolveAccountAvatars() }
                    resolveAvatars()
                }
            }
    }

    /** Restore local images before publishing; new senders still resolve progressively. */
    private suspend fun withCachedAvatars(incoming: List<MailMessage>): List<MailMessage> {
        val service = avatars ?: return incoming
        val previous =
            messages.associate {
                (it.sender.accountKey to avatarEmailKey(it.sender.address)) to
                    it.sender.avatar
            }
        val accounts = messages.associate { it.location.accountId to it.location.accountAvatar }
        val restored = mutableMapOf<Pair<String, String>, ResolvedAvatar?>()
        return incoming.map { message ->
            val sender = message.sender
            val key = sender.accountKey to avatarEmailKey(sender.address)
            if (key !in restored) {
                val cached = service.cached(AvatarRequest(sender.accountKey, sender.address), avatarOptions)
                restored[key] = keepIdenticalImage(cached, previous[key])
            }
            val avatar = restored[key]?.copy(fallback = generatedAvatar(sender.name, sender.address))
            val location = message.location.copy(accountAvatar = accounts[message.location.accountId])
            message.copy(sender = sender.copy(avatar = avatar), location = location)
        }
    }

    /** Each account's own address resolves like a sender, for the inbox picker. */
    private suspend fun resolveAccountAvatars() {
        val service = avatars ?: return
        val accounts =
            messages
                .map {
                    it.location
                }.distinctBy { it.accountId }
                .filter { it.accountAddress.isNotBlank() }
        coroutineScope {
            accounts.forEach { account ->
                launch {
                    val request = AvatarRequest(account.accountId, account.accountAddress, account.account)
                    val resolved = service.resolve(request, avatarOptions)
                    val avatar = keepIdenticalImage(resolved, account.accountAvatar) ?: resolved
                    messages =
                        messages.map { message ->
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

    /** Optional network photos update rows without blocking mail or showing errors. */
    private suspend fun resolveAvatars() {
        val service = avatars ?: return
        val senders = messages.map { it.sender }.distinctBy { it.accountKey to avatarEmailKey(it.address) }
        coroutineScope {
            senders.forEach { sender ->
                launch {
                    val resolved =
                        service.resolve(
                            AvatarRequest(sender.accountKey, sender.address, sender.name),
                            avatarOptions,
                        )
                    val avatar = keepIdenticalImage(resolved, sender.avatar) ?: resolved
                    messages =
                        messages.map { message ->
                            if (message.sender.accountKey == sender.accountKey &&
                                avatarEmailKey(message.sender.address) == avatarEmailKey(sender.address)
                            ) {
                                val updated =
                                    avatar.copy(
                                        fallback =
                                            generatedAvatar(
                                                message.sender.name,
                                                message.sender.address,
                                            ),
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

/** Disk restoration may allocate new bytes. Keep a displayed image's identity when its content matches. */
private fun keepIdenticalImage(
    current: ResolvedAvatar?,
    previous: ResolvedAvatar?,
): ResolvedAvatar? {
    val image = previous?.image ?: return current
    return if (current?.image?.bytes?.contentEquals(image.bytes) == true) current.copy(image = image) else current
}

private fun UnavailableAccount.notice(): String =
    when (reason) {
        InboxFailure.SignInRejected -> "$name: the server rejected the sign-in. Reconnect it in Settings."
        InboxFailure.NotPermitted -> "$name: the server doesn't allow this account to read mail."
        InboxFailure.CredentialsUnavailable -> "$name: the saved password couldn't be read. Reconnect it in Settings."
        InboxFailure.NoMail -> "$name: this account doesn't offer mail over JMAP."
        InboxFailure.Unreachable -> "$name: couldn't reach the server."
        InboxFailure.ServerProblem -> "$name: the server returned an error."
    }
