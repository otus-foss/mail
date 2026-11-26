package com.otusfoss.mail.app

import androidx.compose.runtime.snapshotFlow
import com.otusfoss.core.avatar.AvatarOptions
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.mail.core.mailbox.folderTree
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.MailScopeChoice
import com.otusfoss.mail.core.messages.domain.MailboxScope
import com.otusfoss.mail.core.messages.domain.accountId
import com.otusfoss.mail.core.messages.domain.prefetch
import com.otusfoss.mail.core.messages.presentation.MailEdits
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.MessageMarking
import com.otusfoss.mail.core.messages.presentation.MessageMoves
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.feature.folders.presentation.mayMark
import com.otusfoss.mail.feature.inbox.presentation.FilterLookups
import com.otusfoss.mail.feature.inbox.presentation.InboxFeedState
import com.otusfoss.mail.feature.inbox.presentation.mailScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * The Inbox's feed and every list page's feeds, the actions on their messages, and the filters
 * each page keeps. Feeds show [edits] and load avatars as the [behavior] says; [mailScope] is whose
 * mail the pages show. One per [MailStates].
 */
internal class ListStates(
    private val services: MailServices,
    scope: CoroutineScope,
    private val behavior: MailBehaviorState,
    private val edits: MailEdits,
    private val folders: FolderStates,
    private val dates: () -> DateFormatter,
    mailScope: () -> MailScopeChoice,
) {
    val feed =
        feedIn(
            behavior.behavior.list.inbox
                .mailScope(),
            scope,
        )

    init {
        scope.launch { prefetch(feed) }
    }

    /** The feeds of the list pages open now, besides the Inbox's [feed]. */
    private val pageFeeds = mutableSetOf<InboxFeedState>()

    val pageFilters = PageFilters()

    val filterLookups = FilterLookups(services.mail, services.people) { mailScope().accountId?.let(::setOf) }

    private val marking = MessageMarking(edits)

    /**
     * The mail of [mailScope], loaded in [pageScope], so leaving a folder page stops its loads and
     * avatar lookups. It shows edits and reloads with [refreshPages] until then. Avatars share the
     * inbox's cache.
     */
    fun feedOf(
        mailScope: MailScope,
        pageScope: CoroutineScope,
    ): InboxFeedState = feedIn(mailScope, pageScope).also { feed -> opened(feed, pageScope) }

    private fun feedIn(
        mailScope: MailScope,
        scope: CoroutineScope,
    ): InboxFeedState {
        val feed = InboxFeedState(services.mail, scope, services.avatars, behavior::avatarOptions, mailScope, dates)
        val stop = edits.observe(feed.edits)
        scope.coroutineContext.job.invokeOnCompletion { stop() }
        return feed
    }

    /**
     * Brings the bodies of [feed]'s first rows to the device as it shows them, where the setting
     * and the network allow, so they open without a download and offline. The Inbox and every
     * opened list page do. Rows must hold still for [PREFETCH_DELAY] first, so a run of edits
     * fetches once. Only the page's own mail counts, not what a search or a filter shows in its
     * place.
     */
    private suspend fun prefetch(feed: InboxFeedState) {
        snapshotFlow {
            feed.messages
                .take(PREFETCH_ROWS)
                .takeIf { feed.showsOwnMail }
                .orEmpty()
        }.distinctUntilChangedBy { rows -> rows.map { it.id } }
            .collectLatest { rows ->
                delay(PREFETCH_DELAY)
                services.bodies.prefetch(rows, behavior.behavior.read.prefetch, services.network)
            }
    }

    private fun opened(
        feed: InboxFeedState,
        pageScope: CoroutineScope,
    ) {
        pageFeeds += feed
        pageScope.coroutineContext.job.invokeOnCompletion { pageFeeds -= feed }
        pageScope.launch { prefetch(feed) }
    }

    /** Loads the open list pages again, apart from the Inbox; [keepReport] keeps a failed action's notice. */
    fun refreshPages(keepReport: Boolean = false) = pageFeeds.toList().forEach { it.refresh(keepReport = keepReport) }

    /** Loads the Inbox and every open list page again, as when what their rows show changed. */
    fun refreshAll() {
        feed.refresh()
        refreshPages()
    }

    /** Every account's folders in folder order, once each by role or name, for search to choose from. */
    fun searchMailboxes(): List<MailboxScope> =
        folders.all.accounts
            .flatMap { account -> account.folderTree().map { it.folder } }
            .map { MailboxScope(null, it.role?.title ?: it.mailboxName, it.role) }
            .distinct()

    /** Moves, marks, and rules for messages shown by [feed]. */
    fun messageActions(feed: InboxFeedState): MessageActions =
        MessageActions(
            moves =
                MessageMoves(
                    targets = folders.filing::moveTargets,
                    listed = folders.filing::listedTargets,
                    onMove = { message, target -> folders.filing.move(feed, message, target) },
                    suggested = folders.filing::suggestedTarget,
                    futureSorting = { folders.sorting.futureSorting(it, feed) },
                ),
            marks = marking.marks(feed, folders.all::mayMark),
            ruleForSender = folders.senderRules::start,
            readLater = { message -> folders.filing.readLater(feed, message)?.takeIf { behavior.behavior.readLater } },
            saved = { behavior.behavior.actions },
            markReadOnOpen = { behavior.behavior.read.markReadOnOpen },
            cleanup = folders.cleaning.cleanup(feed),
        )
}

/** How many of the Inbox's first rows have their bodies fetched ahead; the bodies' own limit is the same. */
private const val PREFETCH_ROWS = 20

/** How long the Inbox's first rows hold still before their bodies are fetched. */
private val PREFETCH_DELAY = 400.milliseconds
