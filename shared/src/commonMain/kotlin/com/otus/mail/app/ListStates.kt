package com.otus.mail.app

import com.otus.avatar.AvatarOptions
import com.otus.dateformat.DateFormatter
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.messages.domain.MailboxScope
import com.otus.mail.core.messages.domain.accountId
import com.otus.mail.core.messages.presentation.MailEdits
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.messages.presentation.MessageMarking
import com.otus.mail.core.messages.presentation.MessageMoves
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.folders.presentation.mayMark
import com.otus.mail.feature.inbox.presentation.FilterLookups
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

/**
 * The Inbox's feed and every list page's feeds, the actions on their messages, and the filters
 * each page keeps. Feeds show [edits] and load avatars with [avatarOptions]; [mailScope] is whose
 * mail the pages show. One per [MailStates].
 */
internal class ListStates(
    private val services: MailServices,
    scope: CoroutineScope,
    private val behavior: MailBehaviorState,
    private val edits: MailEdits,
    private val folders: FolderStates,
    private val dates: () -> DateFormatter,
    private val avatarOptions: () -> AvatarOptions,
    mailScope: () -> MailScopeChoice,
) {
    val feed = feedOf(behavior.behavior.inbox.mailScope(), scope)

    val pageFilters = PageFilters()

    val filterLookups = FilterLookups(services.mail, services.people) { mailScope().accountId?.let(::setOf) }

    private val marking = MessageMarking(edits)

    /**
     * The mail of [mailScope], loaded in [pageScope], so leaving a folder page stops its loads and
     * avatar lookups. It shows edits until then. Avatars share the inbox's cache.
     */
    fun feedOf(
        mailScope: MailScope,
        pageScope: CoroutineScope,
    ): InboxFeedState {
        val feed = InboxFeedState(services.mail, pageScope, services.avatars, avatarOptions, mailScope, dates)
        val stop = edits.observe(feed.edits)
        pageScope.coroutineContext.job.invokeOnCompletion { stop() }
        return feed
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
            readLater = { folders.filing.readLater(feed, it) },
            behavior = { behavior.behavior },
            cleanup = folders.cleaning.cleanup(feed),
        )
}
