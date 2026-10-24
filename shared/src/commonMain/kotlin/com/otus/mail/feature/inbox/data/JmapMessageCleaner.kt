package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.JmapSession
import com.otus.jmap.QueryMethod
import com.otus.jmap.SetMethod
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EMAIL_FOLDERS_PROPERTIES
import com.otus.mail.core.mail.EmailFoldersType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.inMailbox
import com.otus.mail.core.mail.pointer
import com.otus.mail.feature.inbox.domain.Deletion
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageCleaner
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Not spam is an `Email/set` patch of `mailboxIds` and `keywords` (RFC 8621, sections 4.1.1 and
 * 4.6) for each email of the row's conversation. Deleting destroys each email that is only in its
 * folder and takes any other one out of it, in `Email/set` calls of at most the session's
 * `maxObjectsInSet` objects (RFC 8620, section 2). Emptying finds the folder's messages with
 * `Email/query` and `Email/get` first.
 */
class JmapMessageCleaner(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : MessageCleaner {
    override suspend fun notSpam(
        message: MailMessage,
        inboxId: String,
        keywords: Boolean,
    ): Boolean {
        val shown = message.location.mailboxId
        if (shown.isEmpty() || shown == inboxId || message.emailId.isEmpty()) return false
        val patches =
            message.listedMembers().associate { member ->
                member.emailId to
                    buildJsonObject {
                        if (member.folderId != inboxId) put("mailboxIds/${pointer(member.folderId)}", JsonNull)
                        put("mailboxIds/${pointer(inboxId)}", JsonPrimitive(true))
                        if (keywords) {
                            put("keywords/$JUNK_KEYWORD", JsonNull)
                            put("keywords/$NOT_JUNK_KEYWORD", JsonPrimitive(true))
                        }
                    }
            }
        return updateAccountEmails(connections, logins, message.location.accountId, patches).containsAll(patches.keys)
    }

    override suspend fun delete(messages: List<MailMessage>): Set<String> =
        messages
            .filter { it.emailId.isNotEmpty() && it.location.mailboxId.isNotEmpty() }
            .groupBy { it.location.accountId }
            .flatMap { (loginId, group) ->
                val stored = group.flatMap { it.listedMembers() }.distinctBy { it.emailId }
                val removed = remove(loginId) { stored }.emailIds
                group.filter { row -> removed.containsAll(row.listedMembers().map { it.emailId }) }.map { it.id }
            }.toSet()

    override suspend fun empty(
        accountId: String,
        folderId: String,
    ): Deletion = remove(accountId) { folderEmails(folderId) }

    /** Removes the emails [find] returns from their folders, batch by batch; complete only when every one left. */
    private suspend fun remove(
        loginId: String,
        find: suspend MailAccount.() -> List<FolderEmail>,
    ): Deletion {
        val removed = mutableSetOf<String>()
        // A failed batch keeps the ones before it, so the caller knows what left.
        return try {
            val account = mailAccount(connections, logins, loginId) ?: return Deletion(emptySet(), complete = false)
            val emails = account.find()
            emails.chunked(account.connection.session().setBatch).forEach { removed += account.removeBatch(it) }
            Deletion(removed, complete = removed.size == emails.size)
        } catch (expected: JmapException) {
            Deletion(removed, complete = false)
        } catch (expected: LoginStoreException) {
            Deletion(removed, complete = false)
        }
    }

    private suspend fun MailAccount.removeBatch(emails: List<FolderEmail>): Set<String> {
        val (elsewhere, onlyHere) = emails.partition { it.elsewhere }
        val result =
            connection.call(
                SetMethod(
                    EmailType,
                    id,
                    update =
                        elsewhere.associate {
                            it.emailId to
                                buildJsonObject { put("mailboxIds/${pointer(it.folderId)}", JsonNull) }
                        },
                    destroy = onlyHere.map { it.emailId },
                ),
            )
        return result.destroyed.toSet() + result.updated.keys
    }

    /**
     * Every email in [folderId], page by page. Paging finishes before anything changes, so
     * deleting cannot shift the pages. A page with no new ids ends it, even if the server repeats one.
     */
    private suspend fun MailAccount.folderEmails(folderId: String): List<FolderEmail> {
        val page = connection.session().getBatch
        val found = linkedMapOf<String, FolderEmail>()
        while (true) {
            val emails =
                connection.request {
                    val query =
                        call(
                            QueryMethod(
                                EmailType,
                                id,
                                inMailbox(folderId),
                                limit = page,
                                extra = mapOf("position" to JsonPrimitive(found.size)),
                            ),
                        )
                    call(
                        GetMethod(
                            EmailFoldersType,
                            id,
                            JmapIds.Result(query.resultOf("/ids")),
                            EMAIL_FOLDERS_PROPERTIES,
                        ),
                    )
                }
            val fresh = emails.list.filter { it.id !in found }
            if (fresh.isEmpty()) return found.values.toList()
            fresh.forEach { found[it.id] = FolderEmail(it.id, folderId, it.mailboxIds.keys) }
        }
    }
}

/**
 * Each email of this row's conversation in the folder it is listed in, or the row's own email
 * when it stands alone. Each decides from its own mailboxes whether deleting destroys it.
 */
private fun MailMessage.listedMembers(): List<FolderEmail> =
    conversation
        ?.members
        ?.filter { it.emailId.isNotEmpty() }
        ?.map { FolderEmail(it.emailId, it.mailboxId.ifEmpty { location.mailboxId }, it.mailboxIds) }
        ?: listOf(FolderEmail(emailId, location.mailboxId, location.mailboxIds))

/** An email in [folderId] that is [elsewhere] when it is in other mailboxes too. */
private class FolderEmail(
    val emailId: String,
    val folderId: String,
    mailboxIds: Set<String>,
) {
    val elsewhere: Boolean = (mailboxIds - folderId).isNotEmpty()
}

/** Objects per `/set` call: the server's `maxObjectsInSet`, or a modest default when it names none. */
internal val JmapSession.setBatch: Int
    get() = limit(maxObjectsInSet)

/** Objects per `/get` call, which also sizes each page of a query. */
private val JmapSession.getBatch: Int
    get() = limit(capabilityObjects[JmapCapability.CORE]?.get("maxObjectsInGet")?.jsonPrimitive?.longOrNull)

private fun limit(advertised: Long?): Int = advertised?.coerceIn(1L, DEFAULT_BATCH.toLong())?.toInt() ?: DEFAULT_BATCH

/** RFC 8620 suggests servers allow at least 500; staying below it keeps requests small. */
private const val DEFAULT_BATCH = 256

internal const val JUNK_KEYWORD = "\$junk"
internal const val NOT_JUNK_KEYWORD = "\$notjunk"
