package com.otus.mail

import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.core.categories.domain.InboxCategories
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MessageContent
import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.core.messages.domain.RemoteImages
import com.otus.mail.core.messages.presentation.ListNudge
import com.otus.mail.core.messages.presentation.components.MailScopeSwitch
import com.otus.mail.core.settings.domain.QuickViewModule
import com.otus.mail.core.settings.domain.ReadQuickModule
import com.otus.mail.core.settings.presentation.InboxPills
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.MailboxSearch
import com.otus.mail.feature.inbox.presentation.OpenRequest
import com.otus.mail.feature.inbox.presentation.QuickViewContext
import com.otus.mail.feature.inbox.presentation.RowLook
import com.otus.mail.feature.inbox.presentation.components.ReadView
import com.otus.mail.feature.read.domain.ConversationMessages
import com.otus.mail.feature.read.domain.KnownPeople
import com.otus.mail.feature.read.presentation.NewsletterSteps
import com.otus.mail.feature.read.presentation.ReadContext
import com.otus.mail.feature.read.presentation.ReadDisplay
import com.otus.mail.feature.read.presentation.ReadEffects
import com.otus.mail.feature.read.presentation.ReadFiles
import com.otus.mail.feature.read.presentation.ReadFolders
import com.otus.mail.feature.read.presentation.ReadLoads
import com.otus.mail.feature.read.presentation.ReadPrivacy
import com.otus.mail.feature.read.presentation.ReadReplies
import com.otus.mail.feature.read.presentation.ReadServices

@DslMarker
internal annotation class ContextDsl

/**
 * An Inbox page's inputs for a test: the unified inbox with nothing wired, until the block sets
 * what the test needs, such as `onCompose` or `filters`.
 */
internal fun inboxContext(block: InboxContextBuilder.() -> Unit = {}): InboxContext =
    InboxContextBuilder()
        .apply(block)
        .build()

/** The Read view's inputs for a test: no pages, folders, services, or files until the block sets them. */
internal fun readContext(block: ReadContextBuilder.() -> Unit = {}): ReadContext =
    ReadContextBuilder()
        .apply(block)
        .build()

@ContextDsl
internal class InboxContextBuilder {
    var defaultSearch = InboxSearch()
    var title = "Inbox"
    var emptyTitle = "Your inbox is empty"
    var mailboxSearch = MailboxSearch()
    var pills = InboxPills()
    var onCompose: (ComposeRequest) -> Unit = {}
    var onOpenDraft: (MailMessage) -> Unit = {}
    var filters: InboxFilterState? = null
    var filterOptions = FilterOptions()
    var rowLook = RowLook()
    var nudge: ListNudge? = null
    var onLoadMore: () -> Unit = {}
    var readView = ReadView {}
    var scope: MailScopeSwitch? = null
    var quick = QuickViewContext(QuickViewModule.Defaults, targets = emptyList())
    var opening: OpenRequest? = null
    var categories: InboxCategories? = null

    fun build(): InboxContext =
        InboxContext(
            defaultSearch = defaultSearch,
            title = title,
            emptyTitle = emptyTitle,
            mailboxSearch = mailboxSearch,
            pills = pills,
            onCompose = onCompose,
            onOpenDraft = onOpenDraft,
            filters = filters,
            filterOptions = filterOptions,
            rowLook = rowLook,
            nudge = nudge,
            onLoadMore = onLoadMore,
            readView = readView,
            scope = scope,
            quick = quick,
            opening = opening,
            categories = categories,
        )
}

@ContextDsl
internal class ReadContextBuilder {
    var pages = ConversationMessages { null }
    var folders = ReadFolders(of = { emptyList() }, cameFrom = { emptySet() }, newFolder = null)
    var privacy =
        ReadPrivacy(
            setting = { RemoteImages.Always },
            allowed = { emptyList() },
            known = KnownPeople { emptySet() },
            allowSender = {},
        )
    var services =
        ReadServices(
            newsletters = null,
            headers = null,
            blocks = null,
            edits = null,
            person = null,
            files = ReadFiles(downloads = null, saved = null, printer = null),
            categories = null,
        )
    var effects = ReadEffects(ToastState(), launch = {}, NewsletterSteps())
    var display =
        ReadDisplay(showQuotes = { false }, zen = { false }, quickView = { ReadQuickModule.Defaults }, setZen = {})
    var own = OwnAddresses(emptyMap())
    var loadContent: suspend (MailMessage) -> MessageContent? = { null }
    var onCompose: (ComposeRequest) -> Unit = {}

    fun build(): ReadContext =
        ReadContext(
            ReadLoads(pages, loadContent),
            folders,
            privacy,
            services,
            effects,
            display,
            ReadReplies(own, onCompose),
        )
}
