package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.QueryMethod
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.withAccount
import com.otusfoss.mail.core.mail.EMAIL_FOLDERS_PROPERTIES
import com.otusfoss.mail.core.mail.EmailFoldersType
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.inMailbox
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mail.mailboxPatch
import com.otusfoss.mail.core.messages.domain.Deletion
import com.otusfoss.mail.core.messages.domain.MailMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deletes emails for good: each email only in its folder is destroyed, any other one is taken out
 * of it, in `Email/set` calls of at most the session's `maxObjectsInSet` objects (RFC 8620,
 * section 2). Each batch rereads membership and conditions its write on that Email state, so a
 * concurrent move cannot destroy a copy filed elsewhere. What left the folder leaves [cache] too.
 * It needs the server: a deletion is decided by what the server holds now, so it never waits offline.
 */
internal class EmailRemoval(
    private val connections: JmapConnections,
    private val cache: MailCache,
) {
    /** Removes the emails [find] returns from their folders, batch by batch; complete only when every one left. */
    suspend fun remove(
        loginId: String,
        find: suspend MailAccount.() -> List<FolderEmail>,
    ): Deletion {
        val removed = mutableSetOf<String>()
        // A failed batch keeps the ones before it, so the caller knows what left.
        return try {
            val account = connections.mailAccount(loginId) ?: return Deletion(emptySet(), complete = false)
            val emails = account.find()
            val batchSize = minOf(account.session.setBatch, account.session.getBatch)
            emails.chunked(batchSize).forEach { removed += account.removeBatch(it) }
            Deletion(removed, complete = removed.size == emails.size)
        } catch (expected: JmapException) {
            Deletion(removed, complete = false)
        } catch (expected: LoginStoreException) {
            Deletion(removed, complete = false)
        }
    }

    private suspend fun MailAccount.removeBatch(emails: List<FolderEmail>): Set<String> {
        val folders = emails.associate { it.emailId to it.folderId }
        val fresh =
            connection.call(
                GetMethod(EmailFoldersType, id, JmapIds.Of(folders.keys.toList()), EMAIL_FOLDERS_PROPERTIES),
            )
        val (present, absent) =
            fresh.list
                .mapNotNull { email ->
                    folders[email.id]?.let { folder -> FolderEmail(email.id, folder, email.mailboxIds.keys) }
                }.partition { it.folderId in it.mailboxIds }
        val alreadyRemoved = absent.map { it.emailId }.toSet() + fresh.notFound
        if (present.isEmpty()) return alreadyRemoved
        val (elsewhere, onlyHere) = present.partition { it.elsewhere }
        val takenOut = elsewhere.associate { it.emailId to mailboxPatch(it.folderId, false) }
        val result =
            connection.call(
                SetMethod(
                    EmailType,
                    id,
                    update = takenOut,
                    destroy = onlyHere.map { it.emailId },
                    ifInState = fresh.state,
                ),
            )
        forget(login.id, result.destroyed.toSet(), takenOut.filterKeys { it in result.updated })
        return alreadyRemoved + result.destroyed + result.updated.keys
    }

    /** Shows the batch's outcome in the cache; a cache failure does not undo what the server did. */
    private suspend fun forget(
        loginId: String,
        destroyed: Set<String>,
        takenOut: Map<String, JsonObject>,
    ) {
        cache.withAccount(loginId) { accountCache ->
            try {
                accountCache.patchEmails(loginId, takenOut)
                if (destroyed.isNotEmpty()) {
                    accountCache.write(loginId) {
                        remove(CacheKind.Email, destroyed)
                        remove(CacheKind.Member, destroyed)
                    }
                }
            } catch (expected: CacheStorageException) {
                // The next sync reads the server's answer.
            }
        }
    }
}

/**
 * Every email in [folderId], page by page. Paging finishes before anything changes, so deleting
 * cannot shift the pages. A page with no new ids ends it, even if the server repeats one.
 */
internal suspend fun MailAccount.folderEmails(folderId: String): List<FolderEmail> {
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
                call(GetMethod(EmailFoldersType, id, JmapIds.Result(query.resultOf("/ids")), EMAIL_FOLDERS_PROPERTIES))
            }
        val fresh = emails.list.filter { it.id !in found }
        if (fresh.isEmpty()) return found.values.toList()
        fresh.forEach { found[it.id] = FolderEmail(it.id, folderId, it.mailboxIds.keys) }
    }
}

/**
 * Each email of this row's conversation in the folder it is listed in, or the row's own email
 * when it stands alone.
 */
internal fun MailMessage.listedMembers(): List<FolderEmail> =
    conversation
        ?.members
        ?.filter { it.emailId.isNotEmpty() }
        ?.map { FolderEmail(it.emailId, it.mailboxId.ifEmpty { location.mailboxId }, it.mailboxIds) }
        ?: listOf(FolderEmail(emailId, location.mailboxId, location.mailboxIds))

/** An email in [folderId] that is [elsewhere] when it is in other mailboxes too. */
internal class FolderEmail(
    val emailId: String,
    val folderId: String,
    val mailboxIds: Set<String>,
) {
    val elsewhere: Boolean = (mailboxIds - folderId).isNotEmpty()
}
