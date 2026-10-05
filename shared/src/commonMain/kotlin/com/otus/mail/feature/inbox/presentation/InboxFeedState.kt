package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.core.avatar.AvatarOptions
import com.otus.mail.core.avatar.AvatarRequest
import com.otus.mail.core.avatar.AvatarService
import com.otus.mail.core.avatar.ResolvedAvatar
import com.otus.mail.core.avatar.avatarEmailKey
import com.otus.mail.core.avatar.generatedAvatar
import com.otus.mail.feature.inbox.domain.InboxFailure
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Loaded inbox messages. A refresh replaces any refresh still in flight. */
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
) {
    var messages by mutableStateOf(emptyList<MailMessage>())
        private set
    var loading by mutableStateOf(false)
        private set

    /** Shown when some accounts could not be loaded; messages from the others remain. */
    var notice by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job =
            scope.launch {
                loading = true
                try {
                    val snapshot = source.inbox()
                    messages = withCachedAvatars(snapshot.messages)
                    notice = snapshot.unavailable.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.notice() }
                } catch (expected: InboxStorageException) {
                    notice = "Couldn't load saved accounts. Try again in Settings."
                } finally {
                    loading = false
                }
                resolveAvatars()
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
        val restored = mutableMapOf<Pair<String, String>, ResolvedAvatar?>()
        return incoming.map { message ->
            val sender = message.sender
            val key = sender.accountKey to avatarEmailKey(sender.address)
            if (key !in restored) {
                val cached = service.cached(AvatarRequest(sender.accountKey, sender.address), avatarOptions)
                restored[key] = keepIdenticalImage(cached, previous[key])
            }
            val avatar = restored[key]?.copy(fallback = generatedAvatar(sender.name, sender.address))
            message.copy(sender = sender.copy(avatar = avatar))
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
