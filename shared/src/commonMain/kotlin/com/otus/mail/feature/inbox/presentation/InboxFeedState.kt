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
import com.otus.mail.core.avatar.mailAvatarOptions
import com.otus.mail.feature.inbox.domain.InboxFailure
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Loaded messages of the unified inbox or one folder, or of the mailboxes a search or a folder
 * pill shows in their place. A refresh replaces any refresh still in flight. Each refresh looks up
 * avatars with the [avatarOptions] of that moment, so a changed setting applies on the next one.
 */
class InboxFeedState(
    private val source: MailSource,
    private val scope: CoroutineScope,
    private val avatars: AvatarService? = null,
    private val avatarOptions: () -> AvatarOptions = { mailAvatarOptions(websiteIcons = true) },
    defaultScope: MailScope = MailScope.Inbox,
) {
    /**
     * The page's own messages, such as the inbox with Spam and Trash as the user chose. A new one
     * loads at once while the page shows its own messages, not while a search shows others.
     */
    var defaultScope = defaultScope
        set(scope) {
            val showingOwn = mailScope == field
            field = scope
            if (showingOwn) show(null)
        }

    /** The applied filters, which the server runs; a new value loads again from the start. */
    var query: MailQuery = MailQuery()
        set(value) {
            if (value == field) return
            field = value
            messages = emptyList()
            refresh()
        }

    /** What the list loads: the page's own messages, or what a search or folder pill shows in their place. */
    var mailScope by mutableStateOf(defaultScope)
        private set

    var messages by mutableStateOf(emptyList<MailMessage>())
        private set
    var loading by mutableStateOf(false)
        private set

    /** Shown when some accounts could not be loaded; messages from the others remain. */
    var notice by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null
    private var request = 0
    private val pending = PendingChanges()

    // The options the shown avatars were found with.
    private var shownOptions: AvatarOptions? = null

    /** Shows [text] in place of the last load's notice, such as a failed change. */
    fun report(text: String) {
        notice = text
    }

    /**
     * Shows [transform] of the loaded message with [id] before the server saves it. Loads keep the
     * change until it is confirmed; rolling it back applies [undo].
     */
    fun change(
        id: String,
        transform: (MailMessage) -> MailMessage,
        undo: (MailMessage) -> MailMessage,
    ): LocalChange {
        messages = messages.map { if (it.id == id) transform(it) else it }
        return track(pending.add(id, scope = null, edit = transform)) {
            messages = messages.map { if (it.id == id) undo(it) else it }
        }
    }

    /** Takes [id] off the list before the server moves it. Rolling back restores it only in the same scope. */
    fun remove(id: String): LocalChange {
        val before = messages.firstOrNull { it.id == id }
        val previousScope = mailScope
        messages = messages.filterNot { it.id == id }
        return track(pending.add(id, previousScope) { null }) {
            if (mailScope == previousScope && before != null) restore(before)
        }
    }

    private fun track(
        key: Int,
        rollBack: () -> Unit,
    ) = LocalChange(
        onConfirm = { pending.confirm(key, request) },
        onRollBack = {
            pending.drop(key)
            rollBack()
        },
    )

    /** Puts [message] back after the server refused to move it, unless a reload already did. */
    private fun restore(message: MailMessage) {
        if (messages.none { it.id == message.id }) messages = messages + message
    }

    /**
     * Loads [scope] in place of the page's own messages, or returns to them when it is null. The
     * previous scope's messages leave at once, so they never show under the new one.
     */
    fun show(scope: MailScope?) {
        val next = scope ?: defaultScope
        if (next == mailScope) return
        mailScope = next
        messages = emptyList()
        refresh()
    }

    fun refresh() {
        val current = ++request
        val requestedScope = mailScope
        val requestedQuery = query
        val options = avatarOptions()
        job?.cancel()
        loading = true
        notice = null
        job =
            scope.launch {
                try {
                    val snapshot = source.messages(requestedScope, requestedQuery)
                    val incoming = withCachedAvatars(snapshot.messages, options)
                    if (current != request) return@launch
                    messages = pending.publish(incoming, current, requestedScope)
                    shownOptions = options
                    notice = snapshot.unavailable.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.notice() }
                } catch (expected: InboxStorageException) {
                    if (current == request) notice = "Couldn't load saved accounts. Try again in Settings."
                } finally {
                    if (current == request) loading = false
                }
                if (current != request) return@launch
                coroutineScope {
                    launch { resolveAccountAvatars(current, options) }
                    resolveAvatars(current, options)
                }
            }
    }

    /**
     * Restore local images before publishing; new senders still resolve progressively. Avatars
     * found with other [options], such as website icons since turned off, are not carried over.
     */
    private suspend fun withCachedAvatars(
        incoming: List<MailMessage>,
        options: AvatarOptions,
    ): List<MailMessage> {
        val service = avatars ?: return incoming
        val shown = messages.takeIf { options == shownOptions }.orEmpty()
        val previous =
            shown.associate {
                (it.sender.accountKey to avatarEmailKey(it.sender.address)) to
                    it.sender.avatar
            }
        val accounts = shown.associate { it.location.accountId to it.location.accountAvatar }
        val restored = mutableMapOf<Pair<String, String>, ResolvedAvatar?>()
        return incoming.map { message ->
            val sender = message.sender
            val key = sender.accountKey to avatarEmailKey(sender.address)
            if (key !in restored) {
                val cached = service.cached(AvatarRequest(sender.accountKey, sender.address), options)
                restored[key] = keepIdenticalImage(cached, previous[key])
            }
            val avatar = restored[key]?.copy(fallback = generatedAvatar(sender.name, sender.address))
            val location = message.location.copy(accountAvatar = accounts[message.location.accountId])
            message.copy(sender = sender.copy(avatar = avatar), location = location)
        }
    }

    /** Each account's own address resolves like a sender, for the inbox picker. */
    private suspend fun resolveAccountAvatars(
        current: Int,
        options: AvatarOptions,
    ) {
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
                    val resolved = service.resolve(request, options)
                    if (current != this@InboxFeedState.request) return@launch
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
    private suspend fun resolveAvatars(
        current: Int,
        options: AvatarOptions,
    ) {
        val service = avatars ?: return
        val senders = messages.map { it.sender }.distinctBy { it.accountKey to avatarEmailKey(it.address) }
        coroutineScope {
            senders.forEach { sender ->
                launch {
                    val resolved =
                        service.resolve(
                            AvatarRequest(sender.accountKey, sender.address, sender.name),
                            options,
                        )
                    if (current != request) return@launch
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
