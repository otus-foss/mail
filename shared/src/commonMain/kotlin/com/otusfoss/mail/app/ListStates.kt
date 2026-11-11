package com.otusfoss.mail.app

import com.otusfoss.core.avatar.AvatarOptions
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.mail.core.mailbox.folderTree
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.MailScopeChoice
import com.otusfoss.mail.core.messages.domain.MailboxScope
import com.otusfoss.mail.core.messages.domain.accountId
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
import kotlinx.coroutines.job

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

    private fun opened(
        feed: InboxFeedState,
        pageScope: CoroutineScope,
    ) {
        pageFeeds += feed
        pageScope.coroutineContext.job.invokeOnCompletion { pageFeeds -= feed }
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
