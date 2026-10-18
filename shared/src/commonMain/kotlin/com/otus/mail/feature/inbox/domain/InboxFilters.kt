package com.otus.mail.feature.inbox.domain

/**
 * What a page's list is narrowed to. The quick pills and the filter panel edit the same value.
 * [status], each of [quick] and [types], and each other choice must all hold. [from] senders,
 * [sentTo] and [to] recipients, and [folders] each match any of their choices. Recipients match
 * in the [receivedIn] fields. A null [account] reads every login.
 */
data class InboxFilters(
    val status: ReadStatus = ReadStatus.All,
    val quick: Set<QuickFacet> = emptySet(),
    val types: Set<TypeFacet> = emptySet(),
    val from: Set<SenderKey> = emptySet(),
    val sentTo: Set<OwnAddress> = emptySet(),
    val to: Set<String> = emptySet(),
    val receivedIn: Set<RecipientField> = RecipientField.Default,
    val account: String? = null,
    val folders: Set<FolderRef> = emptySet(),
    val date: DateFilter = DateFilter.Any,
    val size: SizeFilter = SizeFilter.Any,
    val sort: FilterSort = FilterSort.NewestFirst,
) {
    /** How many choices differ from the defaults, one per pill the row shows; the order is not one. */
    val activeCount: Int
        get() =
            listOf(quick, types, from, sentTo, to, folders).sumOf { it.size } +
                listOf(
                    status != ReadStatus.All,
                    account != null,
                    receivedIn != RecipientField.Default,
                    date != DateFilter.Any,
                    size != SizeFilter.Any,
                ).count { it }

    /** Nothing narrows the list, and it is newest first. */
    val isDefault: Boolean
        get() = this == InboxFilters()

    /** Whether a choice is on that only the loaded threads can tell, so the server cannot count it. */
    val clientOnly: Boolean
        get() = quick.any { it.clientOnly } || types.any { it.clientOnly }

    /** Every address of the chosen senders. */
    val fromAddresses: Set<String>
        get() = from.flatMap { it.addresses }.toSet()

    fun has(facet: QuickFacet): Boolean = facet in quick

    fun with(
        facet: QuickFacet,
        on: Boolean,
    ): InboxFilters = copy(quick = if (on) quick + facet else quick - facet)

    fun has(facet: TypeFacet): Boolean = facet in types

    fun with(
        facet: TypeFacet,
        on: Boolean,
    ): InboxFilters = copy(types = if (on) types + facet else types - facet)

    /** Whether the row pill's filter is on: Unread is the status, the others quick filters. */
    fun has(pill: InboxPill.Filter): Boolean = pill.quick?.let(::has) ?: (status == ReadStatus.Unread)

    fun with(
        pill: InboxPill.Filter,
        on: Boolean,
    ): InboxFilters = pill.quick?.let { with(it, on) } ?: copy(status = if (on) ReadStatus.Unread else ReadStatus.All)

    /** Only [ref]'s folder, a different subfolder choice of the same folder replacing it. */
    fun withFolder(
        ref: FolderRef,
        on: Boolean,
    ): InboxFilters {
        val others = folders.filterNot { it.loginId == ref.loginId && it.mailboxId == ref.mailboxId }.toSet()
        return copy(folders = if (on) others + ref else others)
    }

    /**
     * Whether [message] meets the choices the server cannot check: the reply state and two-sided
     * threads, read from its conversation, and calendar invites. Messages without a conversation
     * meet none of the thread choices.
     */
    fun keepsLoaded(message: MailMessage): Boolean {
        val conversation = message.conversation
        return quick.filter { it.clientOnly }.all { facet ->
            when (facet) {
                QuickFacet.NeedsMyReply -> conversation?.needsMyReply == true
                QuickFacet.AwaitingReply -> conversation?.awaitingReply == true
                QuickFacet.Conversations -> conversation?.exchanged == true
                else -> true
            }
        } && (TypeFacet.CalendarInvites !in types || message.attributes.invite)
    }
}

/** The quick filter a row pill turns on; null for Unread, which sets the status. */
private val InboxPill.Filter.quick: QuickFacet?
    get() =
        when (this) {
            InboxPill.Filter.Unread -> null
            InboxPill.Filter.Attachments -> QuickFacet.Attachments
            InboxPill.Filter.Flagged -> QuickFacet.Flagged
            InboxPill.Filter.Conversations -> QuickFacet.Conversations
        }
