package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.avatar.AvatarOptions
import com.otus.avatar.AvatarService
import com.otus.avatar.avatarOptions
import com.otus.dateformat.DateFormatter
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.presentation.EditObserver
import com.otus.mail.core.messages.presentation.EditedList
import com.otus.mail.core.messages.presentation.MailEdit
import com.otus.mail.core.messages.presentation.empty
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

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
    private val avatarOptions: () -> AvatarOptions = { avatarOptions(websiteIcons = true) },
    defaultScope: MailScope = MailScope.Inbox,
    private val dates: () -> DateFormatter = { DateFormatter() },
) : EditedList {
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
     * The applied filters, which the server runs; a new value loads again from the start. A list
     * shown moments ago comes back at once; otherwise the list stays until the device's matches
     * replace it, so a filter never blanks it first.
     */
    var query: MailQuery = MailQuery()
        set(value) {
            if (value == field) return
            field = value
            autoPages = 0
            val shown = recent.of(mailScope, value)
            if (shown != null) messages = shown
            refresh(cachedFirst = shown == null)
        }

    /** What the list loads: the page's own messages, or what a search or folder pill shows in their place. */
    override var mailScope by mutableStateOf(defaultScope)
        private set

    override var messages by mutableStateOf(emptyList<MailMessage>())
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
    private var autoPages = 0
    private val pending = PendingChanges()
    private val avatars = FeedAvatars(avatars)
    private val recent = RecentLists()

    private var reportedNotice: String? = null

    /** Keeps an action failure visible through the current load, until the next refresh by the user. */
    override fun report(text: String) {
        reportedNotice = text
        notice = text
    }

    /**
     * Shows each edit before the server saves it: a row that leaves this list's scope goes, and a
     * changed row changes. Loads keep the edit until it is kept; a rollback undoes it, and puts a
     * row that left back only while the list still shows that scope.
     */
    internal val edits: EditObserver =
        object : EditObserver {
            private val shown = mutableMapOf<MailEdit, LocalChange>()

            override fun shown(edit: MailEdit) {
                val row = edit.row
                val local =
                    when {
                        edit.leaves != null -> if (edit.leaves == mailScope) remove(edit.rowId) else null
                        row != null -> change(edit.rowId, row, edit.undo)
                        else -> null
                    }
                if (local != null) shown[edit] = local
            }

            override fun settled(
                edit: MailEdit,
                kept: Boolean,
            ) {
                val local = shown.remove(edit) ?: return
                if (kept) local.confirm() else local.rollBack()
            }
        }

    /** Shows [transform] of the row [id] until rolled back, which applies [undo]; see [edits]. */
    internal fun change(
        id: String,
        transform: (MailMessage) -> MailMessage,
        undo: (MailMessage) -> MailMessage,
    ): LocalChange {
        val edited = { list: List<MailMessage> -> list.map { if (it.id == id) transform(it) else it } }
        messages = edited(messages)
        recent.edit(edited)
        return pending.tracked(pending.add(id, scope = null, edit = transform), { request }) {
            messages = messages.map { if (it.id == id) undo(it) else it }
        }
    }

    /** Takes the row [id] off the list until rolled back; see [edits]. */
    internal fun remove(id: String): LocalChange {
        val before = messages.firstOrNull { it.id == id }
        val previousScope = mailScope
        messages = messages.filterNot { it.id == id }
        recent.edit { list -> list.filterNot { it.id == id } }
        return pending.tracked(pending.add(id, previousScope) { null }, { request }) {
            // Put it back after the server refused the move, unless a reload already did.
            if (mailScope == previousScope && before != null && messages.none { it.id == before.id }) {
                messages = messages + before
            }
        }
    }

    /**
     * Loads [scope] in place of the page's own messages, or returns to them when it is null. The
     * previous scope's messages leave at once, so they never show under the new one; the new
     * scope's list shows at once when it was shown moments ago.
     */
    fun show(scope: MailScope?) {
        val next = scope ?: defaultScope
        if (next == mailScope) return
        mailScope = next
        autoPages = 0
        val shown = recent.of(next, query)
        messages = shown.orEmpty()
        refresh(cachedFirst = shown == null)
    }

    /**
     * Loads the scope's messages from the server again, with as many pages as are shown. With
     * [cachedFirst], as for a list left empty by a new scope or a new query, it first shows what
     * the device kept, while [loading] stays true, unless the server answers first. With
     * [keepReport], as for a reload after a change on the server, a reported failure stays.
     */
    override fun refresh() = refresh(cachedFirst = messages.isEmpty())

    fun refresh(
        cachedFirst: Boolean = messages.isEmpty(),
        keepReport: Boolean = false,
    ) {
        loading = true
        loadingMore = false
        if (!keepReport) reportedNotice = null
        notice = reportedNotice
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
                        notice = loadNotice(reportedNotice, expected.storage.notice)
                    }
                } finally {
                    if (load.number == request) onEnd()
                }
                // A failed page stops the pages loading on their own, and the next load resolves avatars.
                if (load.number == request && load.served && continuesOnItsOwn) {
                    autoPages++
                    loadMore()
                }
                if (load.number == request) {
                    avatars.resolve(messages, load.options, { load.number == request }) { edit ->
                        messages = messages.map(edit)
                    }
                }
            }
    }

    /** A list the device narrows shows few rows a page, so [MAX_AUTO_PAGES] more load on their own, then on scroll. */
    private val continuesOnItsOwn get() = query.filters.clientOnly && autoPages < MAX_AUTO_PAGES

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
        val filters = load.query.filters
        val shown =
            if (fromServer && snapshot.hasMore && filters.activeCount > 0) {
                incoming.withShownBeyond(messages, filters.sort)
            } else {
                incoming
            }
        messages = pending.publish(shown, load.number, load.scope)
        recent.keep(load.scope, load.query, messages)
        hasMore = snapshot.hasMore
        avatars.shown(load.options)
        if (fromServer) {
            load.served = true
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val unavailable =
                snapshot.unavailable.takeIf { it.isNotEmpty() }?.joinToString(
                    " ",
                ) { it.notice(now, dates()) }
            notice = loadNotice(reportedNotice, unavailable)
        }
    }
}

/**
 * The change [key] adds as a [LocalChange]: confirming keeps it until the load after [request];
 * rolling back drops it.
 */
private fun PendingChanges.tracked(
    key: Int,
    request: () -> Int,
    rollBack: () -> Unit,
) = LocalChange(
    onConfirm = { confirm(key, request()) },
    onRollBack = {
        drop(key)
        rollBack()
    },
)
