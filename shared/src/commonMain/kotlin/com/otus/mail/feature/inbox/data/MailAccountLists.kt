package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnection
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.ThreadObject
import com.otus.mail.core.mail.mailboxes
import com.otus.mail.core.mail.objects

/** The lists a load keeps for a login, given its mailboxes. */
internal typealias ListingsOf = (List<MailboxObject>) -> List<ListingQuery>

/** One login's mail account, and how many rows a page of its lists holds. */
internal class MailAccountLists(
    private val connection: JmapConnection,
    private val accountId: String,
    private val loginId: String,
    private val cache: MailCache,
    private val page: Int,
    private val now: () -> Long,
) {
    /**
     * Brings the login's cache up to date: the changes since the saved states, then each list of
     * [listingsOf], by its changes where the server can say them, or again, as long as it has
     * loaded so far and at least a page, then only the rows and threads the cache lacks or that
     * changed.
     */
    suspend fun refresh(listingsOf: ListingsOf) {
        val changes = connection.mailChanges(accountId, cache, loginId)
        val queries = listingsOf(changes.mailboxes)
        val saved = cache.listings(loginId, queries)
        val pages = queries.map { PageRequest(it, position = 0, limit = maxOf(page, saved[it.key]?.ids?.size ?: 0)) }
        val listings = queries.map { it.key }.zip(connection.refreshPages(accountId, pages, saved, page)).toMap()
        store(listings, listings.values.flatMap { it.ids }.toSet(), changes)
    }

    /**
     * Adds the next page to each list of [listingsOf] that has more. Lists the cache does not
     * hold yet load with [refresh] instead.
     */
    suspend fun more(listingsOf: ListingsOf) {
        val queries = listingsOf(cache.mailboxes(loginId))
        val saved = cache.listings(loginId, queries)
        if (queries.isEmpty() || saved.size < queries.size) return refresh(listingsOf)
        val growing = queries.filter { saved.getValue(it.key).hasMore }
        val requests = growing.map { PageRequest(it, position = saved.getValue(it.key).ids.size, limit = page) }
        val added = growing.map { it.key }.zip(connection.queryPages(accountId, requests)).toMap()
        // New mail moves every row down, so a page can repeat rows the list already has.
        // The ids only follow later changes when the page came from the same query state.
        val listings =
            added.mapValues { (key, next) ->
                val before = saved.getValue(key)
                Listing(
                    (before.ids + next.ids).distinct(),
                    next.hasMore,
                    next.queryState.takeIf {
                        it ==
                            before.queryState
                    },
                )
            }
        store(listings, added.values.flatMap { it.ids }.toSet(), changes = null)
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
