package com.otus.mail.feature.inbox.data

import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.ThreadObject
import com.otus.mail.core.mail.objects
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.core.messages.data.WantedMail
import com.otus.mail.core.messages.data.boxes
import com.otus.mail.core.messages.data.fetchMail
import com.otus.mail.core.messages.data.memberIds
import com.otus.mail.core.messages.data.saveMail

/** The lists a load keeps for a login, given its mailboxes. */
internal typealias ListingsOf = (List<MailFolder>) -> List<ListingQuery>

/** One login's mail [account], its kept [mailboxes], and how many rows a page of its lists holds. */
internal class MailAccountLists(
    private val account: MailAccount,
    private val mailboxes: Mailboxes,
    private val cache: MailCache,
    private val page: Int,
    private val now: () -> Long,
) {
    private val connection = account.connection
    private val accountId = account.id
    private val loginId = account.login.id

    /**
     * Brings the login's cache up to date: the changes since the saved states, then each list of
     * [listingsOf], by its changes where the server can say them, or again, as long as it has
     * loaded so far and at least a page, then only the rows and threads the cache lacks or that
     * changed.
     */
    suspend fun refresh(listingsOf: ListingsOf) {
        val changes = account.mailChanges(mailboxes, cache)
        val queries = listingsOf(changes.mailboxes)
        val saved = cache.listings(loginId, queries)
        val pages = queries.map { PageRequest(it, position = 0, limit = maxOf(page, saved[it.key]?.ids?.size ?: 0)) }
        val listings = queries.map { it.key }.zip(connection.refreshPages(accountId, pages, saved, page)).toMap()
        store(listings, listings.values.flatMap { it.ids }.toSet(), changes)
    }

    /**
     * Adds the next page to each list of [listingsOf] that has more, after its last row still in
     * its mailbox (see [nextPages]). A list the cache does not hold, such as one dropped to make
     * room for newer ones, loads its first page. Only a login without cached mailboxes refreshes.
     */
    suspend fun more(listingsOf: ListingsOf) {
        val queries = listingsOf(mailboxes.cached(loginId))
        if (queries.isEmpty()) return refresh(listingsOf)
        val saved = cache.listings(loginId, queries)
        val growing = queries.filter { saved[it.key]?.hasMore != false }
        val listings = growing.map { it.key }.zip(nextPages(growing, saved)).toMap()
        val added = listings.flatMap { (key, listing) -> listing.ids - saved[key]?.ids.orEmpty().toSet() }
        store(listings, added.toSet(), changes = null)
    }

    /**
     * Each of [queries] with its next page. A page starts after the list's last row the cache
     * still holds in the list's mailbox, so moving loaded rows away skips none. When no row is
     * left, or the server no longer finds it in the list, the list loads again from the start with
     * a page more; a list [saved] lacks loads its first page.
     */
    private suspend fun nextPages(
        queries: List<ListingQuery>,
        saved: Map<String, Listing>,
    ): List<Listing> {
        val anchors = lastKept(queries, saved)
        val anchored = queries.filter { it.key in anchors }
        val requests = anchored.map { PageRequest(it, limit = page, anchor = anchors.getValue(it.key)) }
        val appended =
            anchored
                .zip(connection.queryPagesOrNull(accountId, requests)) { query, next ->
                    next?.let { query.key to saved.getValue(query.key).appended(it) }
                }.filterNotNull()
                .toMap()
        val restarted = queries.filter { it.key !in appended }
        val reloads = restarted.map { PageRequest(it, limit = page + (saved[it.key]?.ids?.size ?: 0)) }
        val reloaded = restarted.map { it.key }.zip(connection.queryPages(accountId, reloads)).toMap()
        return queries.map { appended[it.key] ?: reloaded.getValue(it.key) }
    }

    /**
     * The last row of each saved listing of [queries] whose cached email is still in the list's
     * mailbox, looked for among the listing's last page of rows.
     */
    private suspend fun lastKept(
        queries: List<ListingQuery>,
        saved: Map<String, Listing>,
    ): Map<String, String> {
        val rowIds = queries.flatMap { saved[it.key]?.ids.orEmpty().takeLast(page) }.toSet()
        val held = cache.objects(loginId, CacheKind.Email, EmailObject.serializer(), rowIds)
        return queries
            .mapNotNull { query ->
                val kept = saved[query.key]?.ids?.lastOrNull { query.mailboxId in held[it]?.boxes.orEmpty() }
                kept?.let { query.key to it }
            }.toMap()
    }

    /** Fetches what [rowIds] need and saves it with [listings] and [changes] in one write. */
    private suspend fun store(
        listings: Map<String, Listing>,
        rowIds: Set<String>,
        changes: MailChanges?,
    ) {
        val wanted = wantedMail(rowIds, changes?.emails ?: ObjectChanges.NONE, changes?.threads ?: ObjectChanges.NONE)
        val fetched = connection.fetchMail(accountId, wanted)
        val use = cache.listingUse(loginId)
        cache.write(loginId) {
            if (changes != null) {
                saveChanges(changes)
                putValue(SYNCED_AT, now().toString())
            }
            saveMail(fetched)
            putListings(listings, use, now())
        }
    }

    /**
     * What [rowIds] need that the cache lacks or that [emails] and [threads] say changed. Every
     * cached email or thread that changed is read again too, so other lists stay current.
     */
    private suspend fun wantedMail(
        rowIds: Set<String>,
        emails: ObjectChanges,
        threads: ObjectChanges,
    ): WantedMail {
        // Without the old emails, the rows' thread calls bring every thread and member again.
        if (emails.reset) return WantedMail(rowIds)
        val held = cache.objects(loginId, CacheKind.Email, EmailObject.serializer(), rowIds - emails.changed)
        val rowThreads =
            held.values
                .map { it.threadId }
                .filter { it.isNotEmpty() }
                .toSet()
        val heldThreads =
            if (threads.reset) {
                emptyMap()
            } else {
                cache.objects(loginId, CacheKind.Thread, ThreadObject.serializer(), rowThreads - threads.changed)
            }
        val memberIds = heldThreads.values.flatMap { it.emailIds }.toSet() - emails.changed
        val heldMembers = held(CacheKind.Member, memberIds) + held(CacheKind.Email, memberIds)
        val changedThreads = if (threads.reset) emptySet() else held(CacheKind.Thread, threads.changed)
        return WantedMail(
            rows = rowIds - held.keys + held(CacheKind.Email, emails.changed),
            threads = rowThreads - heldThreads.keys + changedThreads,
            members = memberIds - heldMembers + held(CacheKind.Member, emails.changed),
        )
    }

    private suspend fun held(
        kind: CacheKind,
        ids: Set<String>,
    ): Set<String> = if (ids.isEmpty()) emptySet() else cache.read(loginId, kind, ids).keys
}

/**
 * This listing with [next], the page after one of its rows, added. The page repeats rows the
 * listing has when its anchor was not the last row, such as after a move the server has not taken
 * yet. The ids only follow later changes when the page came from the same query state.
 */
private fun Listing.appended(next: Listing): Listing =
    Listing((ids + next.ids).distinct(), next.hasMore, next.queryState.takeIf { it == queryState })
