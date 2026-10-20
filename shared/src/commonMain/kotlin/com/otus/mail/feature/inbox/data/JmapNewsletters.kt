package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.QueryResult
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.EmailFolders
import com.otus.mail.core.mail.EmailFoldersType
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mail.NEWEST_FIRST
import com.otus.mail.core.mail.pointer
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailingList
import com.otus.mail.feature.inbox.domain.Newsletters
import com.otus.mail.feature.inbox.domain.Unsubscribe
import com.otus.mail.feature.inbox.domain.UnsubscribeResult
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Mailing lists from their headers (RFC 2369, RFC 2919). A list's older mails are the newest
 * [SCAN] with the same `List-Id`, or from the same sender when it has none, found with
 * `Email/query` (RFC 8621, section 4.4); mail in Trash and Spam does not count.
 */
class JmapNewsletters(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    http: HttpClient,
    drafts: DraftRepository,
    sender: DraftSender,
    private val cache: MailCache = InMemoryMailCache(),
) : Newsletters {
    private val unsubscriber = ListUnsubscriber(http, drafts, sender)

    override suspend fun list(message: MailMessage): MailingList? =
        try {
            mailAccount(connections, logins, message.location.accountId)?.list(message)
        } catch (expected: JmapException) {
            null
        } catch (expected: LoginStoreException) {
            null
        }

    override suspend fun unsubscribe(
        message: MailMessage,
        how: Unsubscribe,
    ): UnsubscribeResult = unsubscriber.unsubscribe(message, how)

    override suspend fun archive(
        accountId: String,
        emailIds: List<String>,
    ): Boolean {
        val patches =
            try {
                if (emailIds.isEmpty()) {
                    emptyMap()
                } else {
                    mailAccount(
                        connections,
                        logins,
                        accountId,
                    )?.archivePatches(emailIds)
                }
            } catch (expected: JmapException) {
                null
            } catch (expected: LoginStoreException) {
                null
            } ?: return false
        return patches.isEmpty() ||
            updateAccountEmails(connections, logins, accountId, patches, cache).containsAll(patches.keys)
    }

    private suspend fun MailAccount.list(message: MailMessage): MailingList? {
        lateinit var emails: JmapCall<GetResult<EmailObject>>
        lateinit var boxes: JmapCall<GetResult<MailboxObject>>
        val first =
            connection.send {
                emails = call(GetMethod(EmailType, id, JmapIds.Of(listOf(message.emailId)), LIST_PROPERTIES))
                boxes = call(GetMethod(MailboxType, id, properties = listOf("id", "role")))
            }
        val email =
            first[emails].list.firstOrNull()?.takeIf { it.listId != null || !it.listUnsubscribe.isNullOrEmpty() }
                ?: return null
        val skipped =
            first[boxes]
                .list
                .filter { it.role in SKIPPED_ROLES }
                .map { it.id }
                .toSet()
        val older =
            olderMail(email)
                .filter { it.id != email.id && it.boxes.none { box -> box in skipped } }
        return MailingList(
            name = listName(email.listId) ?: message.sender.name,
            perWeek = perWeek(listOf(email.receivedAt) + older.map { it.receivedAt }),
            olderIds = older.map { it.id },
        )
    }

    /** The list's mail received before [email], newest first. */
    private suspend fun MailAccount.olderMail(email: EmailObject): List<EmailObject> {
        val listId = listIdentifier(email.listId)
        val address = email.from?.firstOrNull()?.email
        val same =
            when {
                listId != null -> {
                    buildJsonObject {
                        put(
                            "header",
                            JsonArray(listOf("List-Id", listId).map(::JsonPrimitive)),
                        )
                    }
                }

                !address.isNullOrBlank() -> {
                    buildJsonObject { put("from", JsonPrimitive(address)) }
                }

                else -> {
                    return emptyList()
                }
            }
        val filter =
            buildJsonObject {
                put("operator", JsonPrimitive("AND"))
                put(
                    "conditions",
                    JsonArray(listOf(same, buildJsonObject { put("before", JsonPrimitive(email.receivedAt)) })),
                )
            }
        return connection
            .request {
                val query = call(QueryMethod(EmailType, id, filter, NEWEST_FIRST, SCAN))
                call(GetMethod(EmailType, id, JmapIds.Result(query.resultOf("/ids")), OLDER_PROPERTIES))
            }.list
    }

    /** Each email out of every mailbox it is in and into Archive; null without an Archive folder. */
    private suspend fun MailAccount.archivePatches(emailIds: List<String>): Map<String, JsonObject>? {
        lateinit var archive: JmapCall<QueryResult>
        lateinit var folders: JmapCall<GetResult<EmailFolders>>
        val response =
            connection.send {
                archive = call(roleQuery(ARCHIVE_ROLE))
                folders = call(GetMethod(EmailFoldersType, id, JmapIds.Of(emailIds), listOf("id", "mailboxIds")))
            }
        val target = response[archive].ids.firstOrNull() ?: return null
        return response[folders].list.associate { email ->
            email.id to
                buildJsonObject {
                    email.mailboxIds.keys
                        .filter { it != target }
                        .forEach { put("mailboxIds/${pointer(it)}", JsonNull) }
                    put("mailboxIds/${pointer(target)}", JsonPrimitive(true))
                }
        }
    }

    private companion object {
        const val SCAN = 100
        const val ARCHIVE_ROLE = "archive"
        val SKIPPED_ROLES = setOf("trash", "junk")
        val LIST_PROPERTIES =
            listOf("id", "receivedAt", "from", "header:List-Id:asText", "header:List-Unsubscribe:asURLs")
        val OLDER_PROPERTIES = listOf("id", "receivedAt", "mailboxIds")
    }
}

/**
 * How many mails a week arrive at the times [received]: the gaps between them over the weeks they
 * span. Null below [MIN_MAILS] mails or when they all came at once.
 */
internal fun perWeek(received: List<String>): Double? {
    val times = received.mapNotNull { Instant.parseOrNull(it) }
    if (times.size < MIN_MAILS) return null
    val weeks = (times.max() - times.min()) / 7.days
    return if (weeks > 0) (times.size - 1) / weeks else null
}

private const val MIN_MAILS = 3
