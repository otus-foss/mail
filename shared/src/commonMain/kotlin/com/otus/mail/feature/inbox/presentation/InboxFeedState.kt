package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.avatar.AvatarOptions
import com.otus.avatar.AvatarService
import com.otus.mail.core.avatar.mailAvatarOptions
import com.otus.mail.feature.inbox.domain.InboxFailure
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.InboxStorage
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Loaded messages of the unified inbox or one folder, or of the mailboxes a search or a folder
 * pill shows in their place. A refresh or a next page replaces any load still in flight. Each load
 * looks up avatars with the [avatarOptions] of that moment, so a changed setting applies on the
 * next one.
 */
class InboxFeedState(
    private val source: MailSource,
    private val scope: CoroutineScope,
    avatars: AvatarService? = null,
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

    /**
     * The applied filters, which the server runs; a new value loads again from the start. The
     * list stays until the device's matches replace it, so a filter never blanks it first.
     */
    var query: MailQuery = MailQuery()
        set(value) {
            if (value == field) return
            field = value
            refresh(cachedFirst = true)
        }

    /** What the list loads: the page's own messages, or what a search or folder pill shows in their place. */
    var mailScope by mutableStateOf(defaultScope)
        private set

    var messages by mutableStateOf(emptyList<MailMessage>())
        private set
    var loading by mutableStateOf(false)
        private set

    /** Whether older messages remain on the server, which [loadMore] loads. */
    var hasMore by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set

    /** Shown when some accounts could not be loaded; messages from the others remain. */
    var notice by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null
    private var request = 0
    private val pending = PendingChanges()
    private val avatars = FeedAvatars(avatars)

    private var reportedNotice: String? = null

    /** Keeps an action failure visible through the current load, until the next refresh. */
    fun report(text: String) {
        reportedNotice = text
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
            // Put it back after the server refused the move, unless a reload already did.
            if (mailScope == previousScope && before != null && messages.none { it.id == before.id }) {
                messages = messages + before
            }
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

    /**
     * Loads the scope's messages from the server again, with as many pages as are shown. With
     * [cachedFirst], as for a list left empty by a new scope or a new query, it first shows what
     * the device kept, while [loading] stays true, unless the server answers first.
     */
    fun refresh(cachedFirst: Boolean = messages.isEmpty()) {
        loading = true
        loadingMore = false
        reportedNotice = null
        notice = null
        start(onEnd = { loading = false }) { load ->
            if (cachedFirst) launch { showCached(load) }
            source.messages(load.scope, load.query)
        }
    }

    /** Loads the next page of each mailbox that has more. Does nothing while any load runs. */
    fun loadMore() {
        if (!hasMore || loading || loadingMore) return
        loadingMore = true
        start(onEnd = { loadingMore = false }) { source.more(it.scope, it.query) }
    }

    /**
     * Replaces any running load with [fetch], whose snapshot shows only while it is the newest
     * load. [onEnd] runs when it ends, unless a newer load replaced it.
     */
    private fun start(
        onEnd: () -> Unit,
        fetch: suspend CoroutineScope.(FeedLoad) -> InboxSnapshot,
    ) {
        val load = FeedLoad(++request, mailScope, query, avatarOptions())
        job?.cancel()
        job =
            scope.launch {
                try {
                    publish(load, fetch(this, load))
                } catch (expected: InboxStorageException) {
                    if (load.number == request) {
                        val failure =
                            when (expected.storage) {
                                InboxStorage.Accounts -> "Couldn't load saved accounts. Try again in Settings."
                                InboxStorage.Cache -> "Couldn't save cached mail. Refresh to try again."
                            }
                        notice = loadNotice(reportedNotice, failure)
                    }
                } finally {
                    if (load.number == request) onEnd()
                }
                if (load.number == request) {
                    avatars.resolve(messages, load.options, { load.number == request }) { edit ->
                        messages = messages.map(edit)
                    }
                }
            }
    }

    private suspend fun showCached(load: FeedLoad) {
        try {
            publish(load, source.cached(load.scope, load.query), fromServer = false)
        } catch (expected: InboxStorageException) {
            // The server load reports it.
        }
    }

    /**
     * Shows [snapshot] while [load] is the newest load, and only until its server result shows, so
     * a slow read of the device's copy cannot replace newer mail.
     */
    private suspend fun publish(
        load: FeedLoad,
        snapshot: InboxSnapshot,
        fromServer: Boolean = true,
    ) {
        val incoming = avatars.restored(snapshot.messages, messages, load.options)
        if (load.number != request || load.served) return
        messages = pending.publish(incoming, load.number, load.scope)
        hasMore = snapshot.hasMore
        avatars.shown(load.options)
        if (fromServer) {
            load.served = true
            val unavailable = snapshot.unavailable.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.notice() }
            notice = loadNotice(reportedNotice, unavailable)
        }
    }
}

/** One load's request number and inputs. [served] once the server's snapshot shows. */
private class FeedLoad(
    val number: Int,
    val scope: MailScope,
    val query: MailQuery,
    val options: AvatarOptions,
) {
    var served = false
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

private fun loadNotice(
    reported: String?,
    loading: String?,
): String? = listOfNotNull(reported, loading).distinct().joinToString(" ").ifEmpty { null }
