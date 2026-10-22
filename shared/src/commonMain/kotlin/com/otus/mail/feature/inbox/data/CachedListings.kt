package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapException
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.ThreadObject
import com.otus.mail.core.mail.mailboxes
import com.otus.mail.core.mail.objects
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/** The list of each mailbox of [scope] among [mailboxes] that [query] keeps, with its [conditions]. */
internal fun MailScope.listingQueries(
    mailboxes: List<MailboxObject>,
    loginId: String,
    query: MailQuery,
    conditions: List<JsonObject>,
): List<ListingQuery> =
    chosen(mailboxes, loginId, query).map { mailbox ->
        ListingQuery(mailbox.id, mailFilter(listOf(mailbox.id), conditions), query.filters.sort)
    }

/**
 * [login]'s lists of [scope] as the cache holds them, one page per mailbox, mapped like loaded
 * rows. A mailbox without a saved list adds nothing, and a row whose email has left the mailbox
 * since, such as after a move, is left out. A row that no longer reads leaves the login out.
 */
internal suspend fun MailCache.cachedPages(
    login: JmapLogin,
    scope: MailScope,
    query: MailQuery,
    conditions: List<JsonObject>,
    zone: TimeZone,
): List<ListingPage<ListedRow>> {
    val mailboxes = mailboxes(login.id)
    val chosen = scope.chosen(mailboxes, login.id, query)
    val queries = scope.listingQueries(mailboxes, login.id, query, conditions)
    val listings = listings(login.id, queries)
    val held = heldMail(login.id, listings.values.flatMap { it.ids }.toSet())
    val view =
        ThreadView(
            shown = chosen.map { it.id }.toSet(),
            trashAndJunk = mailboxes.filter { it.role in HIDDEN_ROLES }.map { it.id }.toSet(),
            sent = mailboxes.filter { it.role == SENT_ROLE }.map { it.id }.toSet(),
            own = query.addresses[login.id].orEmpty() + login.username,
        )
    return try {
        jmapDecoding {
            chosen.zip(queries).mapNotNull { (mailbox, listingQuery) ->
                listings[listingQuery.key]?.let { listing ->
                    val list = ListedMailbox(login, mailbox.id, scope.nameOf(mailbox), view, zone)
                    ListingPage(listing.ids.mapNotNull { held.row(it, list) }, listing.hasMore)
                }
            }
        }
    } catch (expected: JmapException.NotJmap) {
        emptyList()
    }
}

/** A list row, with when its email arrived, which orders rows across lists. */
internal class ListedRow(
    val message: MailMessage,
    val receivedAt: Instant,
)

/** The mailbox a list shows, and what its rows are mapped with. */
private class ListedMailbox(
    val login: JmapLogin,
    val mailboxId: String,
    val name: String,
    val view: ThreadView,
    val zone: TimeZone,
)

/** The cached row emails of some lists, their threads, and those threads' emails. */
private class HeldMail(
    val rows: Map<String, EmailObject>,
    val threads: Map<String, ThreadObject>,
    val members: Map<String, EmailObject>,
) {
    fun row(
        id: String,
        list: ListedMailbox,
    ): ListedRow? {
        val row = rows[id]?.takeIf { list.mailboxId in it.boxes } ?: return null
        val thread = threads[row.threadId]?.emailIds.orEmpty().mapNotNull { members[it] }
        val login = list.login
        val message =
            row
                .toMessage(login.id, login.name, list.name, list.zone, login.username, list.mailboxId)
                .inConversation(row, thread, list.mailboxId, list.view, list.zone)
        return ListedRow(message, Instant.parse(row.receivedAt))
    }
}

/** Thread emails come from the member objects, or from row objects for emails that are rows too. */
private suspend fun MailCache.heldMail(
    loginId: String,
    rowIds: Set<String>,
): HeldMail {
    val rows = objects(loginId, CacheKind.Email, EmailObject.serializer(), rowIds)
    val threadIds =
        rows.values
            .map { it.threadId }
            .filter { it.isNotEmpty() }
            .toSet()
    val threads = objects(loginId, CacheKind.Thread, ThreadObject.serializer(), threadIds)
    val memberIds = threads.values.flatMap { it.emailIds }.toSet()
    val members = objects(loginId, CacheKind.Member, EmailObject.serializer(), memberIds)
    val rowMembers = objects(loginId, CacheKind.Email, EmailObject.serializer(), memberIds - members.keys)
    return HeldMail(rows, threads, rowMembers + members)
}

/** Roles whose emails a conversation counts only when the list shows them. */
private val HIDDEN_ROLES = setOf("trash", "junk")

private const val SENT_ROLE = "sent"
