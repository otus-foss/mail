package com.otus.mail.feature.inbox.domain

/**
 * What a page's list is narrowed to. The quick pills and the filter panel edit the same value.
 * [show] conditions must all hold. [from] senders, [accounts], [sentTo] addresses, [receivedAs]
 * fields, and [folders] each match any of their choices. Mail in [skipFolders] is left out.
 */
data class InboxFilters(
    val show: Set<ShowFacet> = emptySet(),
    val from: Set<String> = emptySet(),
    val accounts: Set<String> = emptySet(),
    val sentTo: Set<OwnAddress> = emptySet(),
    val receivedAs: Set<RecipientField> = emptySet(),
    val folders: Set<FolderRef> = emptySet(),
    val skipFolders: Set<FolderRef> = emptySet(),
    val date: DateFilter = DateFilter.Any,
    val size: SizeFilter = SizeFilter.Any,
    val sort: FilterSort = FilterSort.NewestFirst,
) {
    /** How many choices differ from the defaults, one per pill the row shows; the order is not one. */
    val activeCount: Int
        get() =
            listOf(show, from, accounts, sentTo, receivedAs, folders, skipFolders).sumOf { it.size } +
                (if (date == DateFilter.Any) 0 else 1) +
                (if (size == SizeFilter.Any) 0 else 1)

    /** Nothing narrows the list, and it is newest first. */
    val isDefault: Boolean
        get() = this == InboxFilters()

    fun has(facet: ShowFacet): Boolean = facet in show

    /** These filters with [facet] turned [on] or off; turning it on turns its opposite off. */
    fun with(
        facet: ShowFacet,
        on: Boolean,
    ): InboxFilters = copy(show = if (on) show - setOfNotNull(facet.opposite) + facet else show - facet)

    fun has(pill: InboxPill.Filter): Boolean = has(pill.facet)

    fun with(
        pill: InboxPill.Filter,
        on: Boolean,
    ): InboxFilters = with(pill.facet, on)

    /** Only [ref]'s folder, a different subfolder choice of the same folder replacing it. */
    fun withFolder(
        ref: FolderRef,
        on: Boolean,
    ): InboxFilters = copy(folders = folders.toggled(ref, on))

    /** [ref]'s folder left out, or no longer left out. */
    fun withSkipped(
        ref: FolderRef,
        on: Boolean,
    ): InboxFilters = copy(skipFolders = skipFolders.toggled(ref, on))
}

private fun Set<FolderRef>.toggled(
    ref: FolderRef,
    on: Boolean,
): Set<FolderRef> {
    val others = filterNot { it.loginId == ref.loginId && it.mailboxId == ref.mailboxId }.toSet()
    return if (on) others + ref else others
}

/** Mail the user sent, which waits for a reply while it is the latest of its conversation. */
fun MailMessage.fromAny(addresses: Set<String>): Boolean =
    addresses.any { it.equals(sender.address, ignoreCase = true) }
