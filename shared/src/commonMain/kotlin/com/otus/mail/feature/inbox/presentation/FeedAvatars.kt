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
import kotlinx.coroutines.yield

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
        val avatars = service ?: return incoming
        val kept = shown.takeIf { options == shownOptions }.orEmpty()
        val previous = kept.associate { it.sender.key() to it.sender.avatar }
        val accounts = kept.associate { it.location.accountId to it.location.accountAvatar }
        val restored = mutableMapOf<SenderKey, ResolvedAvatar?>()
        return incoming.map { message ->
            val sender = message.sender
            val key = sender.key()
            if (key !in restored) {
                val cached = avatars.cached(sender.avatarRequest(), options)
                restored[key] = keepIdenticalImage(cached, previous[key])
            }
            val avatar = restored[key]?.copy(fallback = generatedAvatar(sender.name, sender.address))
            val location = message.location.copy(accountAvatar = accounts[message.location.accountId])
            message.copy(sender = sender.copy(avatar = avatar), location = location)
        }
    }

    /**
     * Resolves the senders and accounts of [messages] and applies the results with [edit] while
     * [isCurrent] says the load that asked is still the newest. Results that arrive together, such
     * as images read from the device, go in one [edit], so the list changes once for them.
     */
    suspend fun resolve(
        messages: List<MailMessage>,
        options: AvatarOptions,
        isCurrent: () -> Boolean,
        edit: ((MailMessage) -> MailMessage) -> Unit,
    ) {
        val avatars = service ?: return
        val batch = AvatarBatch(isCurrent, edit)
        coroutineScope {
            launch { resolveAccounts(avatars, messages, options, isCurrent, batch::add) }
            resolveSenders(avatars, messages, options, isCurrent, batch::add)
        }
    }

    /** Each account's own address resolves like a sender, for the inbox picker. */
    private suspend fun resolveAccounts(
        service: AvatarService,
        messages: List<MailMessage>,
        options: AvatarOptions,
        isCurrent: () -> Boolean,
        edit: suspend ((MailMessage) -> MailMessage) -> Unit,
    ) {
        coroutineScope {
            messages
                .map { it.location }
                .distinctBy { it.accountId }
                .filter { it.accountAddress.isNotBlank() }
                .forEach { account ->
                    launch {
                        val request = AvatarRequest(account.accountId, account.accountAddress, account.account)
                        val resolved = service.resolve(request, options)
                        if (!isCurrent()) return@launch
                        val avatar = keepIdenticalImage(resolved, account.accountAvatar) ?: resolved
                        edit { message -> message.withAccountAvatar(account.accountId, avatar) }
                    }
                }
        }
    }

    private suspend fun resolveSenders(
        service: AvatarService,
        messages: List<MailMessage>,
        options: AvatarOptions,
        isCurrent: () -> Boolean,
        edit: suspend ((MailMessage) -> MailMessage) -> Unit,
    ) {
        coroutineScope {
            messages
                .map { it.sender }
                .distinctBy { it.key() }
                .forEach { sender ->
                    launch {
                        val resolved = service.resolve(sender.avatarRequest(), options)
                        if (!isCurrent()) return@launch
                        val avatar = keepIdenticalImage(resolved, sender.avatar) ?: resolved
                        edit { message -> message.withSenderAvatar(sender, avatar) }
                    }
                }
        }
    }
}

/**
 * Collects the edits of avatars that arrive while the feed's thread is busy, and applies them with
 * [apply] in one change once it is free, while [isCurrent]. The feed's scope runs on one thread.
 */
private class AvatarBatch(
    private val isCurrent: () -> Boolean,
    private val apply: ((MailMessage) -> MailMessage) -> Unit,
) {
    private val pending = mutableListOf<(MailMessage) -> MailMessage>()

    suspend fun add(edit: (MailMessage) -> MailMessage) {
        pending += edit
        // The first edit of a batch waits for the others that are ready, then applies them all.
        if (pending.size > 1) return
        yield()
        val edits = pending.toList()
        pending.clear()
        if (isCurrent()) apply { message -> edits.fold(message) { edited, next -> next(edited) } }
    }
}

/** A brand logo shows only for mail that passed DMARC, so a sender's other mail looks up apart. */
private data class SenderKey(
    val accountKey: String,
    val email: String,
    val brandDomain: String?,
)

private fun MailSender.key() = SenderKey(accountKey, avatarEmailKey(address), brandDomain)

private fun MailSender.avatarRequest() = AvatarRequest(accountKey, address, name, brandDomain)

/** Disk restoration may allocate new bytes. Keep a displayed image's identity when its content matches. */
private fun keepIdenticalImage(
    current: ResolvedAvatar?,
    previous: ResolvedAvatar?,
): ResolvedAvatar? {
    val image = previous?.image ?: return current
    return if (current?.image?.bytes?.contentEquals(image.bytes) == true) current.copy(image = image) else current
}

private fun MailMessage.withAccountAvatar(
    accountId: String,
    avatar: ResolvedAvatar,
): MailMessage = if (location.accountId == accountId) copy(location = location.copy(accountAvatar = avatar)) else this

private fun MailMessage.withSenderAvatar(
    person: MailSender,
    avatar: ResolvedAvatar,
): MailMessage =
    if (sender.key() == person.key()) {
        copy(sender = sender.copy(avatar = avatar.copy(fallback = generatedAvatar(sender.name, sender.address))))
    } else {
        this
    }
