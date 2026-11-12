package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.GetResult
import com.otusfoss.core.jmap.JmapCall
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.QueryMethod
import com.otusfoss.core.jmap.anyOf
import com.otusfoss.core.jmap.filterCondition
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.mail.core.mail.EmailAddress
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.NEWEST_FIRST
import com.otusfoss.mail.core.mail.isSeen
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mail.receivedTime
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.PersonHistories
import com.otusfoss.mail.core.messages.domain.PersonHistory
import com.otusfoss.mail.core.messages.domain.PersonThread
import com.otusfoss.mail.core.messages.domain.SharedPerson
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonPrimitive

/**
 * A person's threads from one `Email/query` on `from`, `to`, and `cc` (RFC 8621, section 4.4),
 * one email per thread, newest first. The server may match an address loosely, such as by
 * substring, so only mail that names the same address stays. The people shared with them come
 * from the same [SEARCHED] threads, so their counts cover recent mail, not all of it.
 */
internal class JmapPersonHistories(
    private val connections: JmapConnections,
    private val mailboxes: Mailboxes,
) : PersonHistories {
    override suspend fun of(
        accountId: String,
        address: String,
        own: (String) -> Boolean,
    ): PersonHistory? =
        jmapCallOr(null) {
            connections.mailAccount(accountId)?.history(address, own)
        }

    private suspend fun MailAccount.history(
        address: String,
        own: (String) -> Boolean,
    ): PersonHistory {
        lateinit var found: JmapCall<GetResult<EmailObject>>
        val filter = anyOf(listOf("from", "to", "cc").map { filterCondition(it, JsonPrimitive(address)) })
        val (folders, response) =
            mailboxes.send(this) {
                val query = call(QueryMethod(EmailType, id, filter, NEWEST_FIRST, SEARCHED, COLLAPSED))
                found = call(GetMethod(EmailType, id, JmapIds.Result(query.resultOf("/ids")), PROPERTIES))
            }
        val bins =
            folders
                .filter { it.isBin }
                .map { it.id }
                .toSet()
        val threads =
            response[found]
                .list
                .filter { email -> email.boxes.none(bins::contains) && email.people.any { it.isSame(address) } }
                .sortedByDescending { it.receivedTime }
        val zone = TimeZone.currentSystemDefault()
        return PersonHistory(
            recent =
                threads.take(PersonHistories.RECENT).map {
                    PersonThread(
                        it.id,
                        it.threadId,
                        it.subject.orEmpty(),
                        it.preview,
                        receivedAt(it.receivedTime, zone),
                        !it.isSeen,
                    )
                },
            shared = sharedPeople(threads) { !it.isSame(address) && !own(it.email) },
        )
    }

    /** The people [keep] lets through in [threads], most threads first; [threads] run newest first. */
    private fun sharedPeople(
        threads: List<EmailObject>,
        keep: (EmailAddress) -> Boolean,
    ): List<SharedPerson> =
        threads
            .flatMap { email ->
                email.people
                    .filter(keep)
                    .distinctBy { it.email.lowercase() }
                    .map { it to email }
            }.groupBy { (person, _) -> person.email.lowercase() }
            .values
            .map { appearances ->
                val (person, newest) = appearances.first()
                SharedPerson(
                    MailSender(person.name.orEmpty(), person.email),
                    appearances.size,
                    newest.subject.orEmpty(),
                )
            }.sortedByDescending { it.threads }

    private val EmailObject.people: List<EmailAddress>
        get() = from.orEmpty() + to.orEmpty() + cc.orEmpty()

    private fun EmailAddress.isSame(address: String): Boolean = email.equals(address, ignoreCase = true)

    private companion object {
        /** How many threads the lookup reads, for Recent and for the people shared in them. */
        const val SEARCHED = 50
        val COLLAPSED = mapOf("collapseThreads" to JsonPrimitive(true))
        val PROPERTIES =
            listOf("id", "threadId", "subject", "preview", "receivedAt", "mailboxIds", "keywords", "from", "to", "cc")
    }
}
